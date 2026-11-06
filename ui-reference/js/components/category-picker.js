import {state} from "../state.js";
import {demo} from "../demo.js";
import {tr,escapeValue} from "../render-helpers.js";

// main has flat, optional categories; opening/cancelling never changes the draft.
export function categoryPicker(){
  const choices=[{id:null,name:tr("uncategorized")},...demo.categories];
  return `<div class="scrim dialog-wrap" data-action="close-overlay"><div class="overlay-stage">
  <section class="dialog-panel" role="dialog" aria-modal="true" aria-labelledby="picker-title" data-stop-close>
  <h2 id="picker-title">${tr("choose_category")}</h2>
  <div class="category-picker-rows">${choices.map(item=>`<button class="chip category-option expense-main-category ${state.expenseDraft.categoryId===item.id?"selected":""}" data-action="select-category" data-id="${escapeValue(item.id??"")}" aria-pressed="${state.expenseDraft.categoryId===item.id}"><span class="category-choice-label">${escapeValue(item.name)}</span>${state.expenseDraft.categoryId===item.id?'<span aria-hidden="true">✓</span>':""}</button>`).join("")}</div>
  <div class="dialog-actions category-picker-footer"><button data-action="close-overlay">${tr("cancel")}</button></div>
  </section></div></div>`;
}
