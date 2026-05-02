package main

import (
	"dentaku-bff/graph"
	"log"
	"net/http"
	"os"
	"time"

	"github.com/99designs/gqlgen/graphql/handler"
	"github.com/99designs/gqlgen/graphql/handler/extension"
	"github.com/99designs/gqlgen/graphql/handler/lru"
	"github.com/99designs/gqlgen/graphql/handler/transport"
	"github.com/99designs/gqlgen/graphql/playground"
	"github.com/vektah/gqlparser/v2/ast"
	"github.com/gorilla/websocket"
	"github.com/rs/cors"
)

const defaultPort = "8080"

func main() {
	port := os.Getenv("PORT")
	if port == "" {
		port = defaultPort
	}

	// 先ほど作成したモック用のリゾルバを初期化
	resolver := graph.NewResolver()
	srv := handler.New(graph.NewExecutableSchema(graph.Config{Resolvers: resolver}))

	// ==========================================
	// 1. WebSocket の設定 (Subscription 用)
	// ==========================================
	srv.AddTransport(&transport.Websocket{
		KeepAlivePingInterval: 10 * time.Second,
		Upgrader: websocket.Upgrader{
			// 開発用：すべてのオリジン（localhost:5173等）からの接続を許可する
			CheckOrigin: func(r *http.Request) bool {
				return true
			},
			ReadBufferSize:  1024,
			WriteBufferSize: 1024,
		},
	})

	// ==========================================
	// 2. HTTP トランスポートの設定 (Mutation / Query 用)
	// ==========================================
	srv.AddTransport(transport.Options{})
	srv.AddTransport(transport.GET{})
	srv.AddTransport(transport.POST{})

	srv.SetQueryCache(lru.New[*ast.QueryDocument](1000))

	srv.Use(extension.Introspection{})
	srv.Use(extension.AutomaticPersistedQuery{
		Cache: lru.New[string](100),
	})

	// ==========================================
	// 3. CORS ミドルウェアの設定
	// ==========================================
	c := cors.New(cors.Options{
		AllowedOrigins:   []string{"http://localhost:5173"}, // React の開発サーバーを許可
		AllowCredentials: true,
		AllowedHeaders:   []string{"Authorization", "Content-Type"},
		// Debug: true, // CORS でハマった時はここを true にするとログが出ます
	})

	http.Handle("/", playground.Handler("GraphQL playground", "/query"))
	http.Handle("/query", c.Handler(srv))

	log.Printf("connect to http://localhost:%s/ for GraphQL playground", port)
	log.Fatal(http.ListenAndServe(":"+port, nil))
}
