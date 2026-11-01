import {state} from "../state.js";
import {number} from "../i18n.js";
import {tr,monthControls} from "../render-helpers.js";

const panels=[
 ["category_breakdown_title","category_breakdown_description","no_categories","category_breakdown_explanation"],
 ["payment_breakdown_title","payment_breakdown_description","no_payment_methods","payment_breakdown_explanation"],
 ["expense_list_title","expense_list_description","no_expenses",null]
];
export function summary(){
 const keys=panels[state.tab];
 return `<section class="summary fade-in"><p class="intro">${tr("summary_intro")}</p><h1>${tr("expense_summary")}</h1>
 ${monthControls()}<div class="summary-amount"><span class="currency">${tr("ils_symbol")}</span><span class="number">${number(state.language,0)}</span></div>
 <p class="count">${tr("monthly_expense_count",number(state.language,0))}</p>
 <div class="tabs">${["by_category","by_payment_method","all_expenses"].map((key,i)=>`<button class="tab ${state.tab===i?"selected":""}" data-action="tab" data-index="${i}">${tr(key)}</button>`).join("")}</div>
 <div class="summary-panel ${state.tab===2?"all":""}"><h2>${tr(keys[0])}</h2><p class="description">${tr(keys[1])}</p><p class="empty">${tr(keys[2])}</p>${keys[3]?`<p class="explanation">${tr(keys[3])}</p>`:""}</div></section>`;
}
