package graph

import (
	"dentaku-bff/graph/model"
	calcv1 "dentaku-bff/internal/pb/calc/v1"
	"fmt"
	"time"
)

func gqlDigitToProto(d model.Digit) calcv1.Digit {
	switch d {
	case model.DigitZero:
		return calcv1.Digit_DIGIT_ZERO
	case model.DigitOne:
		return calcv1.Digit_DIGIT_ONE
	case model.DigitTwo:
		return calcv1.Digit_DIGIT_TWO
	case model.DigitThree:
		return calcv1.Digit_DIGIT_THREE
	case model.DigitFour:
		return calcv1.Digit_DIGIT_FOUR
	case model.DigitFive:
		return calcv1.Digit_DIGIT_FIVE
	case model.DigitSix:
		return calcv1.Digit_DIGIT_SIX
	case model.DigitSeven:
		return calcv1.Digit_DIGIT_SEVEN
	case model.DigitEight:
		return calcv1.Digit_DIGIT_EIGHT
	case model.DigitNine:
		return calcv1.Digit_DIGIT_NINE
	case model.DigitDot:
		return calcv1.Digit_DIGIT_DOT
	}
	return calcv1.Digit_DIGIT_UNSPECIFIED
}

func protoDigitToGql(d calcv1.Digit) model.Digit {
	switch d {
	case calcv1.Digit_DIGIT_ZERO:
		return model.DigitZero
	case calcv1.Digit_DIGIT_ONE:
		return model.DigitOne
	case calcv1.Digit_DIGIT_TWO:
		return model.DigitTwo
	case calcv1.Digit_DIGIT_THREE:
		return model.DigitThree
	case calcv1.Digit_DIGIT_FOUR:
		return model.DigitFour
	case calcv1.Digit_DIGIT_FIVE:
		return model.DigitFive
	case calcv1.Digit_DIGIT_SIX:
		return model.DigitSix
	case calcv1.Digit_DIGIT_SEVEN:
		return model.DigitSeven
	case calcv1.Digit_DIGIT_EIGHT:
		return model.DigitEight
	case calcv1.Digit_DIGIT_NINE:
		return model.DigitNine
	case calcv1.Digit_DIGIT_DOT:
		return model.DigitDot
	}
	return model.DigitZero
}

func toGqlState(s *calcv1.CalculatorState) *model.CalculatorState {
	if s == nil {
		return &model.CalculatorState{DisplayValue: "0", IsNewInput: true}
	}
	return &model.CalculatorState{
		DisplayValue: s.GetDisplayValue(),
		StoredValue:  s.StoredValue,
		CurrentOp:    s.CurrentOp,
		IsNewInput:   s.GetIsNewInput(),
	}
}

func envelopeToGqlEvent(env *calcv1.EventEnvelope) model.CalcEvent {
	if env == nil || env.Event == nil {
		return nil
	}
	id := fmt.Sprintf("%s-%d", env.GetPersistenceId(), env.GetSeqNr())
	ts := ""
	if env.Timestamp != nil {
		ts = env.Timestamp.AsTime().Format(time.RFC3339Nano)
	}
	switch e := env.Event.Event.(type) {
	case *calcv1.CalcEvent_DigitEntered:
		return model.DigitEntered{ID: id, Timestamp: ts, Digit: protoDigitToGql(e.DigitEntered.GetDigit())}
	case *calcv1.CalcEvent_OperatorSelected:
		return model.OperatorSelected{ID: id, Timestamp: ts, Operator: e.OperatorSelected.GetOperator()}
	case *calcv1.CalcEvent_Calculated:
		return model.Calculated{ID: id, Timestamp: ts, Result: e.Calculated.GetResult()}
	case *calcv1.CalcEvent_Cleared:
		return model.Cleared{ID: id, Timestamp: ts}
	case *calcv1.CalcEvent_Undone:
		return model.Undone{ID: id, Timestamp: ts}
	}
	return nil
}
