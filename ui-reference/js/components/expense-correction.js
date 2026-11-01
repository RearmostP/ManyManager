import {state} from "../state.js";
import {tr,escapeValue} from "../render-helpers.js";
import {parseAmount} from "../preview-model.js";
export function expenseCorrection(){
 return `<div class="scrim dialog-wrap" data-action="close-overlay"><div class="overlay-stage"><div class="dialog-panel" role="dialog" aria-modal="true" aria-labelledby="correction-title" data-stop-close>
 <h2 id="correction-title">${tr("correct_expense_amount")}</h2>
 <label class="field-label" for="correction-amount">${tr("expense_amount")}</label>
 <input id="correction-amount" class="sheet-field" data-correction inputmode="decimal" value="${escapeValue(state.correctionDraft.amount)}">
 <p id="correction-error" role="alert" hidden></p>
 <div class="dialog-actions"><button class="chip" data-action="close-overlay">${tr("cancel")}</button><button class="primary" data-action="confirm-expense-correction" ${parseAmount(state.correctionDraft.amount)>0?"":"disabled"}>${tr("confirm_amount_correction")}</button></div>
 </div></div></div>`;
}
