import {state} from "../state.js";
import {demo} from "../demo.js";
import {parseCivilDate} from "../dates.js";
import {shortDate} from "../i18n.js";
import {tr,money,escapeValue} from "../render-helpers.js";
import {categoryField,canSaveExpense} from "./expense-sheet.js";

export function expenseDetailsBody(){
 const saved=demo.expenses.find(item=>item.id===state.expenseEditId);
 if(!saved)state.expenseEditing=false;
 const editing=state.expenseEditing&&Boolean(saved),draft=state.expenseDraft;
 const field=(key,label,display,control)=>`<div class="expense-detail-field" data-field="${key}">
 ${editing?`<label class="detail-label" for="details-${key}">${tr(label)}</label>`:`<p class="detail-label">${tr(label)}</p>`}
 ${editing?control:`<p class="detail-value" data-value="${key}">${escapeValue(display)}</p>`}</div>`;
 const input=(key,type,value)=>`<input id="details-${key}" class="sheet-field" type="${type}" data-details="${key}" value="${escapeValue(value)}" ${type==="date"?'dir="ltr"':""}>`;
 const choices=(key,items,selected,optional)=>`<select id="details-${key}" class="sheet-field" data-details="${key}">
 ${optional?`<option value="" ${selected==null?"selected":""}>${tr("uncategorized")}</option>`:""}
 ${items.map(item=>`<option value="${escapeValue(item.id)}" ${selected===item.id?"selected":""}>${escapeValue(item.name)}</option>`).join("")}</select>`;
 const category=demo.categories.find(item=>item.id===saved?.categoryId)?.name??tr("uncategorized");
 const source=demo.sources.find(item=>item.id===saved?.sourceId)?.name??"";
 return `<header class="expense-details-header"><h2 id="expense-details-title">${tr("expense_details")}</h2>
 <button class="chip secondary-action" data-action="close-overlay">${tr("close")}</button></header>
 ${saved?`<div class="expense-details-fields">
 ${field("amount","expense_amount",money(saved.amountMinor),input("amount","text",draft.amount).replace('type="text"','type="text" inputmode="decimal"'))}
 ${field("date","expense_date",shortDate(state.language,parseCivilDate(saved.date)),input("date","date",draft.date))}
 ${field("categoryId","expense_category",category,categoryField("details-categoryId"))}
 ${editing?`<button class="detail-new-category" data-action="add-category">${tr("new_category")}</button>`:""}
 ${field("sourceId","payment_method",source,choices("sourceId",demo.sources,draft.sourceId,false))}
 ${editing||saved.note?field("note","optional_note",saved.note??"",`<textarea id="details-note" class="sheet-field" data-details="note">${escapeValue(draft.note)}</textarea>`):""}
 </div>`:`<p role="status">${tr("expense_missing_record")}</p>`}
 ${saved?(editing?`<div class="expense-details-actions">
 <div class="expense-details-primary-actions"><button class="primary" data-action="save-expense-details" ${canSaveExpense()?"":"disabled"}>${tr("save_expense_changes")}</button>
 <button class="chip secondary-action" data-action="cancel-expense-edit">${tr("cancel")}</button></div>
 <button class="detail-delete" data-action="delete-expense">${tr("delete_expense")}</button></div>`:
 `<div class="expense-details-read-actions"><button class="primary" data-action="begin-expense-edit">${tr("edit")}</button></div>`):""}`;
}
export function expenseDetails(){
 return `<div class="scrim dialog-wrap" data-expense-details-wrapper><div class="overlay-stage">
 <section id="expense-details" class="dialog-panel expense-details-panel" role="dialog" aria-modal="true" aria-labelledby="expense-details-title">${expenseDetailsBody()}</section>
 </div></div>`;
}
