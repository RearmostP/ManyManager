import {state} from "../state.js";
import {tr,periodLabel,escapeValue} from "../render-helpers.js";
export function canConfirmSourceEdit(){return Boolean(state.sourceDraft.name.trim())}
export function sourceDialog(){
  const edit=state.overlay==="edit-source";
  return `<div class="scrim dialog-wrap" data-action="close-overlay"><div class="overlay-stage"><div class="dialog-panel" data-stop-close role="dialog" aria-modal="true" aria-labelledby="source-dialog-title">
    <h2 id="source-dialog-title">${tr(edit?"edit_source":"add_source")}</h2>
    <label for="source-name">${tr("source_name")}</label>
    <input id="source-name" class="sheet-field" data-source-draft="name" value="${escapeValue(state.sourceDraft.name)}">
    <p id="source-error" class="form-error" role="alert" hidden></p>
    <div class="dialog-actions"><button data-action="close-overlay">${tr("cancel")}</button><button class="primary" data-action="${edit?"confirm-edit-source":"confirm-add-source"}">${tr(edit?"save_expense_changes":"add_source")}</button></div>
    </div></div></div>`;
}
export function budgetDialog(){
  return `<div class="scrim dialog-wrap" data-action="close-overlay"><div class="overlay-stage"><div class="dialog-panel" data-stop-close role="dialog" aria-modal="true" aria-labelledby="budget-dialog-title">
    <h2 id="budget-dialog-title">${tr("budget_for_month",periodLabel(state.language,state.dialogMonth))}</h2>
    <label for="period-budget">${tr("monthly_budget")}</label>
    <input id="period-budget" class="sheet-field" inputmode="decimal" value="${escapeValue(state.budgetDraft)}">
    <p id="source-error" class="form-error" role="alert" hidden></p>
    <div class="dialog-actions"><button data-action="close-overlay">${tr("cancel")}</button><button class="primary" data-action="confirm-budget">${tr("update_budget")}</button></div>
    </div></div></div>`;
}
