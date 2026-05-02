package graph_test

import (
	"testing"

	"dentaku-bff/graph"
	// "dentaku-bff/graph/model"
	"context"

	"github.com/99designs/gqlgen/client"
	"github.com/99designs/gqlgen/graphql/handler"
)

func TestCalculatorBFF_Mock(t *testing.T) {
	// 1. リゾルバ（モック状態）とテストクライアントの初期化
	resolver := graph.NewResolver()
	srv := handler.NewDefaultServer(graph.NewExecutableSchema(graph.Config{Resolvers: resolver}))
	c := client.New(srv)

	// 2. 初期状態のテスト (Query)
	t.Run("初期状態は0であること", func(t *testing.T) {
		var resp struct {
			CurrentState struct {
				DisplayValue string
			}
		}
		// Queryを実行し、結果を構造体にマッピング
		c.MustPost(`query { currentState { displayValue } }`, &resp)

		if resp.CurrentState.DisplayValue != "0" {
			t.Errorf("Expected '0', but got '%s'", resp.CurrentState.DisplayValue)
		}
	})

	// 3. 数字入力のテスト (Mutation -> Query)
	t.Run("数字の5を入力すると画面が5になること", func(t *testing.T) {
		var mutResp struct {
			PressDigit bool
		}
		// Mutationを実行
		c.MustPost(`mutation { pressDigit(digit: "5") }`, &mutResp)

		if !mutResp.PressDigit {
			t.Errorf("Mutation failed")
		}

		// Stateが更新されたかQueryで確認
		var queryResp struct {
			CurrentState struct {
				DisplayValue string
			}
		}
		c.MustPost(`query { currentState { displayValue } }`, &queryResp)

		if queryResp.CurrentState.DisplayValue != "5" {
			t.Errorf("Expected '5', but got '%s'", queryResp.CurrentState.DisplayValue)
		}
	})

	t.Run("さらに2を入力すると画面が52になること (桁の追加)", func(t *testing.T) {
		var mutResp struct{ PressDigit bool }
		c.MustPost(`mutation { pressDigit(digit: "2") }`, &mutResp)
		
		var queryResp struct{ CurrentState struct{ DisplayValue string } }
		c.MustPost(`query { currentState { displayValue } }`, &queryResp)
		if queryResp.CurrentState.DisplayValue != "52" {
			t.Errorf("Expected '52', but got '%s'", queryResp.CurrentState.DisplayValue)
		}
	})

	t.Run("演算子(+)を入力すると、裏に値が保持され、画面はそのまま(次の入力待ち)になること", func(t *testing.T) {
		var mutResp struct{ PressOperator bool }
		// 注: schema.resolvers.go に PressOperator の実装が必要です
		c.MustPost(`mutation { pressOperator(operator: "+") }`, &mutResp)

		var queryResp struct {
			CurrentState struct {
				DisplayValue string
				StoredValue  *float64
				CurrentOp    *string
			}
		}
		c.MustPost(`query { currentState { displayValue storedValue currentOp } }`, &queryResp)
		
		if queryResp.CurrentState.DisplayValue != "52" {
			t.Errorf("Display should remain '52', but got '%s'", queryResp.CurrentState.DisplayValue)
		}
		if queryResp.CurrentState.StoredValue == nil || *queryResp.CurrentState.StoredValue != 52.0 {
			t.Errorf("StoredValue should be 52.0")
		}
		if queryResp.CurrentState.CurrentOp == nil || *queryResp.CurrentState.CurrentOp != "+" {
			t.Errorf("CurrentOp should be '+'")
		}
	})

	t.Run("不正な演算子(x)を入力するとエラーになること", func(t *testing.T) {
		// client.MustPost はエラー時にpanicするので、エラーを検証する場合は client.Post を使います
		var mutResp struct{ PressOperator bool }
		err := c.Post(`mutation { pressOperator(operator: "x") }`, &mutResp)
		
		if err == nil {
			t.Errorf("Expected an error for invalid operator, but got nil")
		}
	})

	t.Run("Clear と Equals の一連の計算と、Subscriptionによるリアルタイム更新をテストする", func(t *testing.T) {
		ctx, cancel := context.WithCancel(context.Background())
		defer cancel()

		// 1. リゾルバのインターフェースを直接取得して Subscription を開始する
		subCh, err := resolver.Subscription().StateUpdated(ctx)
		if err != nil {
			t.Fatalf("Failed to subscribe: %v", err)
		}

		// 2. サブスクライブ直後に初期状態が Push されてくることを確認
		initialState := <-subCh
		if initialState == nil {
			t.Fatalf("Expected initial state but got nil")
		}

		// 3. Clear を実行して状態を綺麗にする
		_, err = resolver.Mutation().PressClear(ctx)
		if err != nil {
			t.Fatalf("PressClear failed: %v", err)
		}
		
		// Clear 後の状態を受信
		clearState := <-subCh
		if clearState.DisplayValue != "0" {
			t.Errorf("Expected display '0' after clear, but got '%s'", clearState.DisplayValue)
		}

		// 4. 「12 * 2 =」 という一連の操作をシミュレーション
		resolver.Mutation().PressDigit(ctx, "1")
		<-subCh // "1" の通知を消費
		
		resolver.Mutation().PressDigit(ctx, "2")
		<-subCh // "12" の通知を消費

		resolver.Mutation().PressOperator(ctx, "*")
		opState := <-subCh // "*" の通知
		if opState.CurrentOp == nil || *opState.CurrentOp != "*" {
			t.Errorf("Expected operator '*', got %v", opState.CurrentOp)
		}

		resolver.Mutation().PressDigit(ctx, "2")
		<-subCh // "2" の通知を消費

		// 5. 最後に `=` を押す
		_, err = resolver.Mutation().PressEquals(ctx)
		if err != nil {
			t.Fatalf("PressEquals failed: %v", err)
		}

		// 6. 計算結果の通知を受信し、24 になっているか検証！
		finalState := <-subCh
		if finalState.DisplayValue != "24" {
			t.Errorf("Expected final result '24', but got '%s'", finalState.DisplayValue)
		}
		if finalState.StoredValue != nil || finalState.CurrentOp != nil {
			t.Errorf("StoredValue and CurrentOp should be nil after equals")
		}
	})
}