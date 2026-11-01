// Civil dates only. Never convert accounting boundaries through UTC timestamps.
export const PERIOD_START_DAY=10;
export function periodFor(date){
  const start=new Date(date.getFullYear(),date.getMonth()-(date.getDate()<PERIOD_START_DAY?1:0),PERIOD_START_DAY);
  return {start,endExclusive:new Date(start.getFullYear(),start.getMonth()+1,PERIOD_START_DAY)};
}
export function periodKey(date){
  const {start}=periodFor(date);
  return `${start.getFullYear()}-${String(start.getMonth()+1).padStart(2,"0")}`;
}
export function adjacentPeriod(date,direction){
  const {start}=periodFor(date);
  return periodFor(new Date(start.getFullYear(),start.getMonth()+direction,PERIOD_START_DAY)).start;
}
export function lastDate(period){
  return new Date(period.endExclusive.getFullYear(),period.endExclusive.getMonth(),period.endExclusive.getDate()-1);
}
