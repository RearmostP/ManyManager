// Optional visual-review fixture only. Never imported by the default app.
import {addSource,addCategory,createExpense} from "../js/preview-model.js";
import {state} from "../js/state.js";
import {periodFor,civilDate} from "../js/budget-period.js";
const period=periodFor(new Date()),date=civilDate(period.start);
const source=addSource("תקציב משפחתי · Family budget · Семейный бюджет",period.start,120000);
const otherSource=addSource("תקציב משפחתי · Family budget · Семейный бюджет",period.start,0);
const category=addCategory("קניות לבית · Household shopping · Покупки для дома");
const otherCategory=addCategory(category.name);
const note="קנייה שבועית לבית · Weekly household shopping · Покупки для дома. ".repeat(12);
createExpense({amountMinor:18790,date,sourceId:source.id,categoryId:category.id,note});
createExpense({amountMinor:4250,date,sourceId:otherSource.id,categoryId:category.id,note:"קפה ומאפה · Coffee · Кофе"});
createExpense({amountMinor:7600,date,sourceId:source.id,categoryId:otherCategory.id});
createExpense({amountMinor:1250,date,sourceId:otherSource.id,categoryId:null,note:"ללא קטגוריה · Uncategorized · Без категории"});
state.page=1;state.tab=0;
state.expandedSummaryGroups.add("category:"+category.id);
state.expandedSummaryGroups.add("category:"+otherCategory.id);
document.documentElement.dataset.fixtureReady="true";
