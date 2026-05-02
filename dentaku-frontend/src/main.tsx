import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';

// Apollo Client 関連のインポート
import {
  ApolloClient,
  InMemoryCache,
  HttpLink,
  ApolloLink
} from '@apollo/client';

import { ApolloProvider } from '@apollo/client/react';
import { GraphQLWsLink } from '@apollo/client/link/subscriptions';
import { getMainDefinition } from '@apollo/client/utilities';
import { createClient } from 'graphql-ws'; // ← 先ほど追加したパッケージ

// ==========================================
// 1. 通信経路（リンク）のセットアップ
// ==========================================

// HTTP リンク（Mutation / Query 用）: Write 側
const httpLink = new HttpLink({
  uri: 'http://localhost:8080/query' // Go (BFF) のエンドポイント
});

// WebSocket リンク（Subscription 用）: Read 側
const wsLink = new GraphQLWsLink(createClient({
  url: 'ws://localhost:8080/query',
}));

// 通信の振り分けルール
const splitLink = ApolloLink.split(
  ({ query }) => {
    const definition = getMainDefinition(query);
    return (
      definition.kind === 'OperationDefinition' &&
      definition.operation === 'subscription'
    );
  },
  wsLink,     // Subscription なら WebSocket へ
  httpLink,   // それ以外（Mutation等）なら HTTP へ
);

// ==========================================
// 2. Apollo Client の初期化
// ==========================================
const client = new ApolloClient({
  link: splitLink,
  cache: new InMemoryCache(), // キャッシュ機構（今回はほぼ素通り）
});

// ==========================================
// 3. React アプリケーションの描画 (React 18/19 standard)
// ==========================================
const rootElement = document.getElementById('root');
if (!rootElement) throw new Error('Failed to find the root element');

const root = createRoot(rootElement);

root.render(
  <StrictMode>
    <ApolloProvider client={client}>
      <App />
    </ApolloProvider>
  </StrictMode>
);