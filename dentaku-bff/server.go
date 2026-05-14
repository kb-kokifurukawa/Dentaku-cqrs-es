package main

import (
	"dentaku-bff/graph"
	calcv1 "dentaku-bff/internal/pb/calc/v1"
	"log"
	"net/http"
	"os"
	"time"

	"github.com/99designs/gqlgen/graphql/handler"
	"github.com/99designs/gqlgen/graphql/handler/extension"
	"github.com/99designs/gqlgen/graphql/handler/lru"
	"github.com/99designs/gqlgen/graphql/handler/transport"
	"github.com/99designs/gqlgen/graphql/playground"
	"github.com/gorilla/websocket"
	"github.com/rs/cors"
	"github.com/vektah/gqlparser/v2/ast"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

const defaultPort = "8080"

func envOr(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func main() {
	port := envOr("PORT", defaultPort)
	writeAddr := envOr("WRITE_GRPC_ADDR", "localhost:9000")
	readAddr := envOr("READ_GRPC_ADDR", "localhost:9001")
	persistenceID := envOr("PERSISTENCE_ID", "calc-1")

	// ==========================================
	// gRPC clients
	// ==========================================
	writeConn, err := grpc.NewClient(writeAddr,
		grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		log.Fatalf("failed to connect to Write server %s: %v", writeAddr, err)
	}
	defer writeConn.Close()

	readConn, err := grpc.NewClient(readAddr,
		grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		log.Fatalf("failed to connect to Read server %s: %v", readAddr, err)
	}
	defer readConn.Close()

	resolver := &graph.Resolver{
		WriteCommand:  calcv1.NewCommandServiceClient(writeConn),
		WriteHistory:  calcv1.NewEventHistoryServiceClient(writeConn),
		ReadQuery:     calcv1.NewStateQueryServiceClient(readConn),
		ReadStream:    calcv1.NewStateStreamServiceClient(readConn),
		PersistenceID: persistenceID,
	}

	srv := handler.New(graph.NewExecutableSchema(graph.Config{Resolvers: resolver}))

	// ==========================================
	// 1. WebSocket (Subscription)
	// ==========================================
	srv.AddTransport(&transport.Websocket{
		KeepAlivePingInterval: 10 * time.Second,
		Upgrader: websocket.Upgrader{
			CheckOrigin: func(r *http.Request) bool {
				return true
			},
			ReadBufferSize:  1024,
			WriteBufferSize: 1024,
		},
	})

	// ==========================================
	// 2. HTTP (Mutation / Query)
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
	// 3. CORS
	// ==========================================
	c := cors.New(cors.Options{
		AllowedOrigins:   []string{"http://localhost:5173"},
		AllowCredentials: true,
		AllowedHeaders:   []string{"Authorization", "Content-Type"},
	})

	http.Handle("/", playground.Handler("GraphQL playground", "/query"))
	http.Handle("/query", c.Handler(srv))

	log.Printf("BFF listening on :%s (Write=%s, Read=%s, PersistenceID=%s)",
		port, writeAddr, readAddr, persistenceID)
	log.Fatal(http.ListenAndServe(":"+port, nil))
}
