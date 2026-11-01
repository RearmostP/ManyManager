import {state} from "../state.js";
import {previewModel,snapshotFor} from "../preview-model.js";
import {parseCivilDate} from "../budget-period.js";
import {shortDate} from "../i18n.js";
import {number} from "../i18n.js";
import {tr,monthControls,money,escapeValue} from "../render-helpers.js";

const panels=[
 ["category_breakdown_title","category_breakdown_description","no_categories","category_breakdown_explanation"],
 ["payment_breakdown_title","payment_breakdown_description","no_payment_methods","payment_breakdown_explanation"],
 ["expense_list_title","expense_list_description","no_expenses",null]
];
export function summary(){
 const keys=panels[state.tab];
 const snapshot=snapshotFor(state.currentDate);
 const rows=state.tab===2?snapshot.expenses.map(item=>{
   const category=previewModel.categories.find(value=>value.id===item.categoryId)?.name??tr("uncategorized");
   const source=previewModel.sources.find(value=>value.id===item.sourceId)?.name??"";
   const label=[money(item.amountMinor),shortDate(state.language,parseCivilDate(item.date)),category,source,item.note].filter(Boolean).map(escapeValue).join(" · ");
   return `<button class="chip" style="display:block;width:100%;text-align:start;margin-top:12px;white-space:normal" data-action="correct-expense" data-id="${item.id}" aria-label="${label} · ${escapeValue(tr("correct_expense_amount"))}">${label}</button>`;
 }).join(""):"";
 const emptyKey=state.tab===0&&previewModel.categories.length?"no_expenses":
   state.tab===1&&previewModel.sources.length?"no_expenses":keys[2];
 return `<section class="summary fade-in"><p class="intro">${tr("summary_intro")}</p><h1>${tr("expense_summary")}</h1>
 ${monthControls()}<div class="summary-amount"><span class="currency">${tr("ils_symbol")}</span><span class="number">${number(state.language,snapshot.spent/100)}</span></div>
 <p class="count">${tr("monthly_expense_count",number(state.language,snapshot.expenses.length))}</p>
 <div class="tabs">${["by_category","by_payment_method","all_expenses"].map((key,i)=>`<button class="tab ${state.tab===i?"selected":""}" data-action="tab" data-index="${i}">${tr(key)}</button>`).join("")}</div>
 <div class="summary-panel ${state.tab===2?"all":""}"><h2>${tr(keys[0])}</h2><p class="description">${tr(keys[1])}</p>${rows||`<p class="empty">${tr(snapshot.expenses.length?"breakdown_pending":emptyKey)}</p>`}${keys[3]?`<p class="explanation">${tr(keys[3])}</p>`:""}</div></section>`;
}
