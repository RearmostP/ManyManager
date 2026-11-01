import {state} from "../state.js";
import {previewModel} from "../preview-model.js";
import {icon} from "../icons.js";
import {tr,escapeValue} from "../render-helpers.js";

export function expense(){
  const categories=previewModel.categories.map(category=>`<button class="chip ${state.expenseDraft.categoryId===category.id?"selected":""}" data-action="expense-category" data-id="${category.id}">${escapeValue(category.name)}</button>`).join("");
  const sources=previewModel.sources.map(source=>`<button class="chip ${state.expenseDraft.sourceId===source.id?"selected":""}" data-action="expense-source" data-id="${source.id}">${escapeValue(source.name)}</button>`).join("");
  return `<div class="scrim sheet-wrap" data-action="close-overlay"><div class="overlay-stage"><div class="sheet" data-stop-close><div class="drag-area" data-action="drag"><div class="drag-track"></div></div>
  <div class="sheet-head"><h2>${tr("new_expense")}</h2><button class="icon-button" data-action="close-overlay" aria-label="${tr("close")}">${icon("close")}</button></div>
  <label class="field-label" for="amount">${tr("expense_amount")}</label><input id="amount" class="amount-input" data-expense="amount" inputmode="decimal" placeholder="${tr("amount_zero")}" value="${escapeValue(state.expenseDraft.amount)}">
  <div class="section"><p class="field-label">${tr("expense_category")}</p>${categories?`<div class="expense-choices">${categories}</div>`:`<p class="empty">${tr("no_categories")}</p>`}<button class="chip new-category" data-action="add-category">${tr("new_category")}</button></div>
  <div class="section" style="margin-top:15px"><p class="field-label">${tr("payment_method")}</p>${sources?`<div class="expense-choices">${sources}</div>`:`<p class="empty">${tr("no_payment_methods")}</p>`}</div>
  <div class="form-row"><div><label class="field-label" for="expense-date">${tr("expense_date")}</label><input id="expense-date" class="sheet-field" dir="ltr" data-expense="date" value="${escapeValue(state.expenseDraft.date)}"></div>
  <div><label class="field-label" for="expense-note">${tr("optional_note")}</label><textarea id="expense-note" class="sheet-field" data-expense="note" placeholder="${tr("expense_note_hint")}">${escapeValue(state.expenseDraft.note)}</textarea></div></div>
  <button class="primary save disabled" disabled>${tr("save_expense")}</button></div></div></div>`;
}
