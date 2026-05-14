package graph

import (
	calcv1 "dentaku-bff/internal/pb/calc/v1"
)

// Resolver は Write/Read 各 gRPC クライアントを保持する。
// GraphQL のリクエストごとにこれらを呼び出して上流に転送する。
type Resolver struct {
	WriteCommand calcv1.CommandServiceClient
	WriteHistory calcv1.EventHistoryServiceClient
	ReadQuery    calcv1.StateQueryServiceClient
	ReadStream   calcv1.StateStreamServiceClient

	// 全 Aggregate を 1 つに固定（学習用）
	PersistenceID string
}
