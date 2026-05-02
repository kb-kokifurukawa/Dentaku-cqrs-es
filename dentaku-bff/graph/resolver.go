package graph

import (
	"sync"
	"dentaku-bff/graph/model"
)

// Resolver は依存関係や状態を保持する構造体です。
// gqlgen がリクエストごとにここから各種メソッドを呼び出します。
type Resolver struct {
	Mu    sync.Mutex
	State *model.CalculatorState
	Subscribers map[string]chan *model.CalculatorState
}

// NewResolver は初期状態をセットしたリゾルバを返します
func NewResolver() *Resolver {
	return &Resolver{
		State: &model.CalculatorState{
			DisplayValue: "0",
			IsNewInput:   true,
		},
		Subscribers: make(map[string]chan *model.CalculatorState),
	}
}

// 状態が更新されたときに、すべての購読者へ新しい状態を Push するヘルパー関数
func (r *Resolver) NotifyStateUpdated() {
	// 現在の状態のディープコピーを作成（データ競合を防ぐため）
	stateCopy := &model.CalculatorState{
		DisplayValue: r.State.DisplayValue,
		StoredValue:  r.State.StoredValue,
		CurrentOp:    r.State.CurrentOp,
		IsNewInput:   r.State.IsNewInput,
	}

	for _, ch := range r.Subscribers {
		// チャネルがブロックしないように、select を使って非同期に送信する
		select {
		case ch <- stateCopy:
		default:
			// チャネルが詰まっている場合はスキップ（今回は簡易実装）
		}
	}
}