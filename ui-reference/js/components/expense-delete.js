import {state} from "../state.js";
import {tr,money,escapeValue} from "../render-helpers.js";
import {shortDate} from "../i18n.js";
import {parseCivilDate} from "../budget-period.js";
import {previewModel} from "../preview-model.js";

export function expenseDelete(){
 const saved=state.expenseOriginal;
 const details=saved?[money(saved.amountMinor),shortDate(state.language,parseCivilDate(saved.date)),
   previewModel.sources.find(item=>item.id===saved.sourceId)?.name,saved.note].filter(Boolean).join(" · "):"";
 return `<div class="scrim dialog-wrap" data-action="close-overlay"><div class="overlay-stage">
 <div class="dialog-panel" role="alertdialog" aria-modal="true" aria-labelledby="delete-title" aria-describedby="delete-details" data-stop-close>
 <h2 id="delete-title">${tr("delete_expense_question")}</h2>
 <p id="delete-details">${tr("delete_expense_details")} ${escapeValue(details)}</p>
 <div class="dialog-actions"><button class="chip secondary-action" data-action="close-overlay">${tr("cancel")}</button>
 <button class="detail-delete" data-action="confirm-delete-expense">${tr("delete_expense")}</button></div>
 </div></div></div>`;
}
