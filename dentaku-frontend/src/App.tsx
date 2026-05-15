import React from 'react';
import { useMutation, useSubscription } from '@apollo/client/react';
import { gql } from '@apollo/client';

// ==========================================
// 1. GraphQL 定義
// ==========================================

// 【Read側】状態のリアルタイム購読
const STATE_SUBSCRIPTION = gql`
  subscription OnStateUpdated {
    stateUpdated {
      displayValue
      currentOp
    }
  }
`;

interface CalculatorState {
  displayValue: string;
  currentOp: string | null;
  // 他に必要なフィールドがあれば追加
}

interface OnStateUpdatedData {
  stateUpdated: CalculatorState;
}

// 1. pressDigit (数字入力)
type Digit = 'ZERO'|'ONE'|'TWO'|'THREE'|'FOUR'|'FIVE'|'SIX'|'SEVEN'|'EIGHT'|'NINE'|'DOT';
interface PressDigitData { pressDigit: boolean; }
interface PressDigitVars { digit: Digit; }

// 2. pressOperator (演算子入力)
interface PressOpData { pressOperator: boolean; }
interface PressOpVars { operator: string; }

// 3. 引数なしのコマンド (Equals, Clear, Undo)
interface PressEqualsData { pressEquals: boolean; }
interface PressClearData { pressClear: boolean; }
interface UndoData { undo: boolean; }

// 【Write側】各種コマンド（操作）
const PRESS_DIGIT = gql`mutation PressDigit($digit: Digit!) { pressDigit(digit: $digit) }`;
const PRESS_OP = gql`mutation PressOp($operator: String!) { pressOperator(operator: $operator) }`;
const PRESS_EQUALS = gql`mutation { pressEquals }`;
const PRESS_CLEAR = gql`mutation { pressClear }`;
const UNDO = gql`mutation { undo }`;

// ==========================================
// 2. メインコンポーネント
// ==========================================
export default function App() {
  // Read: 状態の購読
  const { data, loading } = useSubscription<OnStateUpdatedData>(STATE_SUBSCRIPTION);
  
  // Write: コマンドの送信関数
  const [pressDigit] = useMutation<PressDigitData, PressDigitVars>(PRESS_DIGIT);
  const [pressOp] = useMutation<PressOpData, PressOpVars>(PRESS_OP);
  const [pressEquals] = useMutation<PressEqualsData>(PRESS_EQUALS);
  const [pressClear] = useMutation<PressClearData>(PRESS_CLEAR);
  const [undo] = useMutation<UndoData>(UNDO);

  // 画面表示用の変数
  const displayValue = data?.stateUpdated?.displayValue || "0";
  const currentOp = data?.stateUpdated?.currentOp || "";

  return (
    <div style={{ maxWidth: '300px', margin: '50px auto', fontFamily: 'monospace' }}>
      
      {/* --- Read領域 (Display) --- */}
      {/* 自分の操作結果だけでなく、サーバーから降ってきた状態をそのまま描画するだけ */}
      <div style={{ background: '#eee', padding: '20px', textAlign: 'right', fontSize: '24px', borderRadius: '4px' }}>
        <div style={{ fontSize: '14px', height: '14px', color: '#666' }}>{currentOp}</div>
        <div>{loading ? "Connecting..." : displayValue}</div>
      </div>

      {/* --- Write領域 (Keypad) --- */}
      {/* 押されたらサーバーに命令を「投げ捨てる（Fire & Forget）」だけ */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '10px', marginTop: '20px' }}>
        <button onClick={() => undo()}>Undo</button>
        <button onClick={() => pressClear()}>C</button>
        <button onClick={() => pressOp({ variables: { operator: '/' } })}>/</button>
        <button onClick={() => pressOp({ variables: { operator: '*' } })}>*</button>

        <button onClick={() => pressDigit({ variables: { digit: 'SEVEN' } })}>7</button>
        <button onClick={() => pressDigit({ variables: { digit: 'EIGHT' } })}>8</button>
        <button onClick={() => pressDigit({ variables: { digit: 'NINE' } })}>9</button>
        <button onClick={() => pressOp({ variables: { operator: '-' } })}>-</button>

        <button onClick={() => pressDigit({ variables: { digit: 'FOUR' } })}>4</button>
        <button onClick={() => pressDigit({ variables: { digit: 'FIVE' } })}>5</button>
        <button onClick={() => pressDigit({ variables: { digit: 'SIX' } })}>6</button>
        <button onClick={() => pressOp({ variables: { operator: '+' } })}>+</button>

        <button onClick={() => pressDigit({ variables: { digit: 'ONE' } })}>1</button>
        <button onClick={() => pressDigit({ variables: { digit: 'TWO' } })}>2</button>
        <button onClick={() => pressDigit({ variables: { digit: 'THREE' } })}>3</button>
        {/* = ボタンは少し大きく */}
        <button onClick={() => pressEquals()} style={{ gridRow: 'span 2' }}>=</button>

        <button onClick={() => pressDigit({ variables: { digit: 'ZERO' } })} style={{ gridColumn: 'span 2' }}>0</button>
        <button onClick={() => pressDigit({ variables: { digit: 'DOT' } })}>.</button>
      </div>
    </div>
  );
}