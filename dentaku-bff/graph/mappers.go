package graph

import (
	"dentaku-bff/graph/model"
	calcv1 "dentaku-bff/internal/pb/calc/v1"
	"fmt"
	"time"
)

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
		return model.DigitEntered{ID: id, Timestamp: ts, Digit: e.DigitEntered.GetDigit()}
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
