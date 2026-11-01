import {state} from "../state.js";
import {previewModel,amountFor,parseAmount,previewAdjustment} from "../preview-model.js";
import {tr,money,periodLabel,escapeValue} from "../render-helpers.js";

export function sourcePreview(){
  const sourceId=state.sourceDraft.sourceId;
  const raw=state.sourceDraft.adjustment;
  const magnitude=raw.trim()===""?0:parseAmount(raw);
  const delta=magnitude===null?null:state.sourceDraft.operation==="add"?magnitude:-magnitude;
  const result=delta===null?null:previewAdjustment(state.dialogMonth,sourceId,delta);
  return result===null?tr("budget_invalid_amount"):tr("budget_after_change",money(result));
}
export function canConfirmSourceEdit(){
  const raw=state.sourceDraft.adjustment;
  const magnitude=raw.trim()===""?0:parseAmount(raw);
  if(!state.sourceDraft.name.trim()||magnitude===null)return false;
  const result=previewAdjustment(state.dialogMonth,state.sourceDraft.sourceId,
    state.sourceDraft.operation==="add"?magnitude:-magnitude);
  return result!==null&&result>=0;
}
export function sourceDialog(){
  const month=tr("budget_for_month",periodLabel(state.language,state.dialogMonth));
  const edit=state.overlay==="edit-source";
  const source=edit?previewModel.sources.find(item=>item.id===state.sourceDraft.sourceId):null;
  const title=tr(edit?"edit_source":"add_source");
  const body=edit?`
    <input id="source-name" class="sheet-field" data-source-draft="name" placeholder="${tr("source_name")}" aria-label="${tr("source_name")}" value="${escapeValue(state.sourceDraft.name)}">
    <p class="current-budget">${tr("current_budget",money(amountFor(state.dialogMonth,source.id)))}</p>
    <label class="field-label" for="source-adjustment">${tr("adjustment_amount")}</label>
    <div class="adjustment-row"><input id="source-adjustment" class="sheet-field" data-source-draft="adjustment" inputmode="decimal" placeholder="${tr("amount_zero")}" value="${escapeValue(state.sourceDraft.adjustment)}">
      <button class="chip ${state.sourceDraft.operation==="add"?"selected":""}" data-action="operation" data-value="add" aria-label="${tr("add_to_budget")}" aria-pressed="${state.sourceDraft.operation==="add"}">${tr("budget_plus_symbol")}</button>
      <button class="chip ${state.sourceDraft.operation==="deduct"?"selected":""}" data-action="operation" data-value="deduct" aria-label="${tr("deduct_from_budget")}" aria-pressed="${state.sourceDraft.operation==="deduct"}">${tr("budget_minus_symbol")}</button>
    </div><p id="source-preview" class="source-preview" aria-live="polite">${sourcePreview()}</p>`:`
    <input id="source-name" class="sheet-field" data-source-draft="name" placeholder="${tr("source_name")}" aria-label="${tr("source_name")}" value="${escapeValue(state.sourceDraft.name)}">
    <label class="field-label initial-label" for="source-initial">${tr("initial_budget")}</label>
    <input id="source-initial" class="sheet-field" data-source-draft="initial" inputmode="decimal" value="${escapeValue(state.sourceDraft.initial)}">`;
  return `<div class="scrim dialog-wrap" data-action="close-overlay"><div class="overlay-stage"><div class="dialog-panel" data-stop-close role="dialog" aria-modal="true" aria-labelledby="source-dialog-title">
    <h2 id="source-dialog-title">${title}</h2><p class="dialog-month">${month}</p>
    ${body}<p id="source-error" class="form-error" role="alert" hidden></p>
    <div class="dialog-actions"><button data-action="close-overlay">${tr("cancel")}</button><button class="primary" data-action="${edit?"confirm-edit-source":"confirm-add-source"}" ${edit&&!canConfirmSourceEdit()?"disabled":""}>${tr(edit?"update_budget":"add_source")}</button></div>
    </div></div></div>`;
}
