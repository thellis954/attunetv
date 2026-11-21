import { test, expect } from '@playwright/test';
const inventory = [
  {id:'Netflix001.App',name:'Netflix'}, {id:'PrimeVid01.App',name:'Prime Video'},
  {id:'AppleTV001.App',name:'Apple TV'}, {id:'Disney0001.App',name:'Disney+'},
  {id:'Hulu000001.App',name:'Hulu'}, {id:'YouTube001.App',name:'YouTube'},
  ...Array.from({length:24},(_,i)=>({id:'Fixture001.App'+i,name:'Channel '+String(i+1).padStart(2,'0')})),
  {id:'AttuneTV01.Companion',name:'AttuneTV'}, {id:'org.tizen.factory',name:'Factory'},
  {id:'Fixture001.backgroundService',name:'Background worker'}, {id:'Fixture001.Hidden',name:'Hidden',show:false},
  {id:'Duplicate1.App',name:'Netflix',installDate:'2000-01-01'}
].map(a=>({show:true,installDate:'2026-01-01',...a}));
async function fixture(page, mode='success') {
  await page.addInitScript(({inventory,mode})=>{
    window.ATTUNE_LAUNCHES=[];
    window.tizen={application:{getCurrentApplication:()=>({appInfo:{id:'AttuneTV01.Companion'},exit:()=>{window.ATTUNE_EXIT=true;}}),getAppsInfo:(ok,fail)=>mode==='inventory-error'?fail({name:'SecurityError'}):ok(inventory),launch:(id,ok,fail)=>{window.ATTUNE_LAUNCHES.push(id);if(mode==='launch-error')fail({name:'NotFoundError'});else ok();}}};
  },{inventory,mode});
  await page.setViewportSize({width:1920,height:1080}); await page.goto('/home.html');
}
test('Samsung Home discovers real inventory, filters services, pins, orders and restores favorites',async({page})=>{
  await fixture(page); await expect(page.locator('#dock .tile')).toHaveCount(6);
  await expect(page.locator('#dock .tile').first()).toBeFocused();
  await page.keyboard.press('ArrowDown'); await expect(page.locator('#apps-panel')).toBeVisible();
  await expect(page.locator('#grid .tile')).toHaveCount(30);
  await page.getByLabel('Search installed apps').fill('Channel 24');
  const tile=page.locator('#grid .tile');await expect(tile).toHaveCount(1);await tile.focus();
  await page.evaluate(()=>document.activeElement.dispatchEvent(new KeyboardEvent('keydown',{keyCode:10133,bubbles:true})));
  await page.getByRole('button',{name:'Pin to Home',exact:true}).click();
  await page.keyboard.press('Escape'); await expect(page.locator('#dock .tile')).toHaveCount(7);
  await page.reload();await expect(page.locator('#dock .tile')).toHaveCount(7);
  await page.locator('#dock .tile').last().focus();await page.getByRole('button',{name:'App options',exact:true}).click();
  await page.getByRole('button',{name:'Move left',exact:true}).click();
  await expect(page.locator('#dock .tile').nth(5)).toHaveAttribute('aria-label','Channel 24');
  await page.locator('#dock .tile').nth(5).click();await expect.poll(()=>page.evaluate(()=>window.ATTUNE_LAUNCHES)).toEqual(['Fixture001.App23']);
});
test('remote grid scroll, focus, empty search and modal Back do not launch or exit unexpectedly',async({page})=>{
  await fixture(page);await page.keyboard.press('ArrowDown');
  await page.locator('#grid .tile').first().focus();
  for(let i=0;i<5;i++)await page.keyboard.press('ArrowDown');
  const box=await page.locator('#grid .tile:focus').boundingBox();const grid=await page.locator('#grid').boundingBox();
  expect(box.y).toBeGreaterThanOrEqual(grid.y);expect(box.y+box.height).toBeLessThanOrEqual(grid.y+grid.height+1);
  await page.getByLabel('Search installed apps').fill('No match');await expect(page.getByText('No apps match that search.')).toBeVisible();
  await page.keyboard.press('Escape');await expect(page.locator('#apps-panel')).toBeHidden();
  await page.keyboard.press('Escape');await expect(page.getByRole('dialog')).toBeVisible();await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toBeHidden();expect(await page.evaluate(()=>window.ATTUNE_EXIT)).toBeUndefined();
  await page.screenshot({path:'test-results/home-browser-fixture.png'});
  for(const viewport of [{width:1280,height:720},{width:960,height:540}]){await page.setViewportSize(viewport);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await expect(page.locator('#dock .tile').first()).toBeInViewport();}
});
test('missing APIs, inventory failure, launch failure and no favorites remain usable',async({page})=>{
  await fixture(page,'launch-error');await page.locator('#dock .tile').first().click();await expect(page.getByRole('alert')).toContainText('Could not open');
  await page.evaluate(()=>localStorage.setItem('attuneHomeFavorites','[]'));await page.reload();await expect(page.locator('#dock .tile')).toHaveCount(0);await expect(page.getByText(/Make this space yours/)).toBeVisible();
  const other=await page.context().newPage();await other.goto('/home.html');await expect(other.getByText(/Open the installed Samsung version/)).toBeVisible();await expect(other.getByLabel('Ask and pair phone')).toHaveAttribute('href','/tv');await other.close();
});
test('inventory API error gives a retry instead of an empty or fabricated dock',async({page})=>{
  await fixture(page,'inventory-error');await expect(page.getByText(/Samsung could not share the app list/)).toBeVisible();await expect(page.getByRole('button',{name:'Try again'})).toBeVisible();await expect(page.locator('#dock .tile')).toHaveCount(0);
});
