import {tr,money,monthControls} from "../render-helpers.js";

export function budget(){
 return `<section class="budget-page fade-in"><h1>${tr("budget_management")}</h1>${monthControls(true)}
 <div class="card budget-card"><div class="caption">${tr("monthly_budget")}</div><div class="total">${money()}</div></div>
 <p class="budget-no-sources">${tr("budget_add_sources_first")}</p><button class="chip selected manage-sources" data-action="manage-sources">${tr("manage_payment_sources")}</button></section>`;
}
