import {state} from "../state.js";
import {tr,periodLabel,periodDescription,escapeValue} from "../render-helpers.js";

export function sourceDialog(){
  const overall=state.overlay==="overall-budget",edit=state.overlay==="edit-source";
  const title=tr(overall?"overall_budget":edit?"source_spending_limit":"add_source");
  return `<div class="scrim dialog-wrap" data-action="close-overlay"><div class="overlay-stage"><div class="dialog-panel" data-stop-close role="dialog" aria-modal="true" aria-labelledby="source-dialog-title">
    <h2 id="source-dialog-title">${title}</h2><p class="dialog-month" aria-label="${escapeValue(periodDescription(state.language,state.dialogMonth))}">${periodLabel(state.language,state.dialogMonth)}</p>
    ${overall?"":`<input id="source-name" class="sheet-field" data-source-draft="name" placeholder="${tr("source_name")}" aria-label="${tr("source_name")}" value="${escapeValue(state.sourceDraft.name)}">`}
    <label class="field-label" for="source-initial">${tr(overall?"overall_budget":"source_spending_limit")}</label>
    <input id="source-initial" class="sheet-field" data-source-draft="initial" inputmode="decimal" aria-label="${tr("optional_amount")}" placeholder="${tr("optional_amount")}" value="${escapeValue(state.sourceDraft.initial)}">
    <p>${tr("limit_independent_hint")}</p>
    <div class="dialog-actions"><button data-action="close-overlay">${tr("cancel")}</button>
    ${edit||overall?`<button data-action="remove-threshold">${tr("remove_threshold")}</button>`:""}
    <button class="primary" data-action="${overall?"confirm-overall":edit?"confirm-edit-source":"confirm-add-source"}">${tr("update_budget")}</button></div>
    </div></div></div>`;
}
