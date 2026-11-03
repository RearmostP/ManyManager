import {state} from "../state.js";
import {demo,summaryData} from "../demo.js";
import {parseCivilDate} from "../dates.js";
import {shortDate,number} from "../i18n.js";
import {tr,monthControls,money,escapeValue} from "../render-helpers.js";

const panels=[
 ["category_breakdown_title","category_breakdown_description","no_categories","category_breakdown_explanation"],
 ["payment_breakdown_title","payment_breakdown_description","no_payment_methods","payment_breakdown_explanation"],
 ["expense_list_title","expense_list_description","no_expenses",null]
];
export function expenseRows(expenses,groupKind=null){
 return expenses.map(item=>{
   const category=demo.categories.find(value=>value.id===item.categoryId)?.name??tr("uncategorized");
   const source=demo.sources.find(value=>value.id===item.sourceId)?.name??"";
   const date=shortDate(state.language,parseCivilDate(item.date));
   const secondary=[date,...(groupKind==="category"?[source]:groupKind==="source"?[category]:[category,source])].filter(Boolean);
   const label=[money(item.amountMinor),...secondary,item.note].filter(Boolean).join(" · ");
   return `<button class="summary-expense-row" data-action="view-expense" data-id="${item.id}" aria-label="${escapeValue(label)} · ${escapeValue(tr("expense_details"))}">
   <span class="expense-row-amount">${escapeValue(money(item.amountMinor))}</span>
   <span class="expense-row-secondary">${secondary.map(escapeValue).join(" · ")}</span>
   ${item.note?`<span class="expense-row-note">${escapeValue(item.note)}</span>`:""}</button>`;
 }).join("");
}
export function summary(){
 const keys=panels[state.tab];
 const snapshot=summaryData(state.currentDate);
 const rows=state.tab===2?expenseRows(snapshot.expenses):"";
 const groups=state.tab===0?snapshot.categoryBreakdown:snapshot.sourceBreakdown;
 const breakdown=groups.map(item=>{
   const label=[item.name??tr("uncategorized"),money(item.spent),tr("breakdown_expense_count",number(state.language,item.expenseCount))];
   const kind=state.tab===0?"category":"source",key=kind+":"+(item.id??"");
   const expanded=state.expandedSummaryGroups.has(key),listId="group-"+key;
   const expenses=snapshot.expenses.filter(expense=>kind==="category"?expense.categoryId===item.id:expense.sourceId===item.id);
   return `<section class="summary-group-card">
   <button class="summary-breakdown-row" data-action="expense-group" data-kind="${kind}" data-id="${item.id??""}" data-breakdown-id="${item.id??""}" aria-expanded="${expanded}" aria-controls="${listId}" aria-label="${escapeValue(label.join(" · "))}">
   <span class="summary-group-name">${escapeValue(label[0])}</span><span class="summary-chevron" aria-hidden="true">${expanded?"⌃":"⌄"}</span>
   <span class="summary-group-total">${escapeValue(label[1])}</span><span class="summary-group-count">${escapeValue(label[2])}</span></button>
   <div id="${listId}" class="summary-inline-expenses" data-inline-group="${key}" ${expanded?"":"hidden"}>${expanded?expenseRows(expenses,kind):""}</div></section>`;
 }).join("");
 return `<section class="summary fade-in"><p class="intro">${tr("summary_intro")}</p><h1>${tr("expense_summary")}</h1>
 ${monthControls()}<div class="summary-amount"><span class="currency">${tr("ils_symbol")}</span><span class="number">${number(state.language,snapshot.spent/100)}</span></div>
 <p class="count">${tr("monthly_expense_count",number(state.language,snapshot.expenses.length))}</p>
 <div class="tabs">${["by_category","by_payment_method","all_expenses"].map((key,i)=>`<button class="tab ${state.tab===i?"selected":""}" data-action="tab" data-index="${i}">${tr(key)}</button>`).join("")}</div>
 <div class="summary-panel ${state.tab===2?"all":""}"><h2>${tr(keys[0])}</h2><p class="description">${tr(keys[1])}</p>${(state.tab===2?rows:breakdown)||`<p class="empty">${tr("no_expenses")}</p>`}${keys[3]?`<p class="explanation">${tr(keys[3])}</p>`:""}</div></section>`;
}
