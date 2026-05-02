# 設計書：CQRS + Event Sourcing パワフル電卓

## 1. 概要
本プロジェクトは、計算機（電卓）というシンプルなドメインを通じて、CQRS（コマンドクエリ責務分離）および Event Sourcing（イベントソーシング）のアーキテクチャを体験するための分散システムである。

「古い電卓」の挙動を再現し、すべての操作をイベントとして記録することで、計算の取り消し（Undo）や、サーバー停止からの状態復元をリアルタイムに実現する。

## 2. システムアーキテクチャ
システムは以下の4つのコンポーネントで構成される分散アーキテクチャを採用する。

### 構成図イメージ
1.  **Frontend (React + TS)**: ユーザーインターフェース。GraphQLを介してBFFと通信。
2.  **BFF (Go + gqlgen)**: GraphQL サーバー。MutationをWriteへ、QueryをReadへルーティングし、Subscriptionでリアルタイム更新を提供。
3.  **Write Server (Scala)**: コマンドの受領とイベントの生成・保存（Event Store）。
4.  **Read Server (Scala)**: イベントログを購読し、現在の画面表示用の状態（Read Model）を構築・保持。

### 通信プロトコル
- **Client ↔ BFF**: GraphQL (HTTP / WebSocket for Subscription)
- **BFF ↔ Write/Read**: gRPC または HTTP/JSON
- **Write ↔ Read ↔ BFF**: WebSocket (イベント/状態のリアルタイムPush)

## 3. 技術スタック
| 役割 | 技術 | 理由 |
| :--- | :--- | :--- |
| **Frontend** | React, TypeScript, Apollo Client | 型安全な開発と強力な Subscription 連携。 |
| **BFF** | Go, gqlgen | 高い並行処理性能による WebSocket/Subscription の効率的な処理。 |
| **Write / Read** | Scala, Http4s / Pekko | ADT とパターンマッチによるイベントと状態遷移の厳密な定義。 |
| **Event Store** | In-Memory (Array/List) | 今回のプロトタイプでは Scala Write Server 内のメモリに保持。 |

## 4. データモデル設計

### 4.1 イベント定義 (Events)
Event Sourcing の核となる「過去に起きた事実」の定義。
- `DigitEntered(digit: String)`: 数字ボタンが押された。
- `OperatorSelected(operator: String)`: 演算子（+, -, *, /）が選択された。
- `EqualsPressed`: `=` が押され、計算が確定した。
- `Cleared`: `C` が押され、すべてがリセットされた。

### 4.2 リードモデル（State）
Read サーバーが保持し、画面に表示するための現在の状態。
- `displayValue`: 画面のメイン表示（文字列）。
- `storedValue`: 演算子押下時に退避された数値。
- `currentOp`: 現在選択されている演算子。
- `isNewInput`: 次の数字入力が「桁の追加」か「新規入力」かを判別するフラグ。

## 5. GraphQL スキーマ
```graphql
interface CalcEvent {
  id: ID!
  timestamp: String!
}

type DigitEntered implements CalcEvent { id: ID!; timestamp: String!; digit: String! }
type OperatorSelected implements CalcEvent { id: ID!; timestamp: String!; operator: String! }
type EqualsPressed implements CalcEvent { id: ID!; timestamp: String! }
type Cleared implements CalcEvent { id: ID!; timestamp: String! }

type CalculatorState {
  displayValue: String!
  storedValue: Float
  currentOp: String
  isNewInput: Boolean!
}

type Query {
  currentState: CalculatorState!
  eventHistory: [CalcEvent!]!
}

type Mutation {
  pressDigit(digit: String!): Boolean!
  pressOperator(operator: String!): Boolean!
  pressEquals: Boolean!
  pressClear: Boolean!
  undo: Boolean!
}

type Subscription {
  stateUpdated: CalculatorState!
}
```

## 6. 主要なワークフロー
### 6.1 コマンド実行から状態更新
1. フロントエンドが pressEquals Mutation を発行。
2. BFF が Write Server へコマンドを転送。
3. Write Server が EqualsPressed イベントを生成し、自身のリストに保存。
4. Write Server が WebSocket 経由で Read Server へイベントを Push。
5. Read Server がイベントを適用（計算実行）し、内部状態を更新。
6. Read Server が更新後の State を BFF へ Push。
7. BFF が stateUpdated Subscription を通じてフロントエンドへ通知。

### 6.2 Undo (1回分の計算取り消し)
1. undo Mutation を受領。
2. Write Server が直近の EqualsPressed イベントを履歴から削除（または無効化）。
3. Write Server が「履歴が変更された」ことを Read Server へ通知。
4. Read Server は初期状態（0）から、残っているイベントをすべて最初から再再生（Replay）して状態を再構築する。

### 6.3 障害復旧
- Read Server 停止時: 再起動時に Write Server から全イベント履歴を取得し、Replay することで瞬時に停止中の操作を反映した状態へ復帰する。

## 7. 実装のポイント
- Scala の型システム: イベントと状態遷移を sealed trait で定義し、ロジックの漏れをコンパイルレベルで防ぐ。
- Go の並行性: BFF で複数クライアントの Subscription 接続を効率的に管理する。
- TypeScript の同期: BFF から流れてくる状態を React の State に反映させ、宣言的に UI を更新する。