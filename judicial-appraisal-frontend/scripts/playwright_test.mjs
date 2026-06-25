import { chromium } from 'playwright';
import fs from 'fs';
import path from 'path';

const SCREENSHOT_DIR = '/Users/myw/.gemini/antigravity/brain/fdbd655f-1e0e-4757-923a-993511ed0afd/scratch/screenshots';
if (!fs.existsSync(SCREENSHOT_DIR)) {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
}

async function login(page, context, username, password = '123456') {
  console.log(`Logging in as ${username}...`);
  await context.clearCookies();
  await page.goto('http://localhost:5173/login');
  await page.waitForTimeout(1000);
  
  // If we are still redirected away from login (maybe local storage auth), we need to clear local storage
  await page.evaluate(() => localStorage.clear());
  await page.goto('http://localhost:5173/login');
  await page.waitForTimeout(1000);

  await page.fill('input[type="text"]', username);
  await page.fill('input[type="password"]', password);
  await page.click('button:has-text("登录")');
  await page.waitForTimeout(2000);
}

async function takeScreenshot(page, name) {
  const filepath = path.join(SCREENSHOT_DIR, `${name}.png`);
  await page.screenshot({ path: filepath, fullPage: true });
  console.log(`Screenshot saved: ${filepath}`);
}

async function fillFormIfExists(page, labelText, value) {
  try {
    const item = page.locator('.el-form-item:visible').filter({ has: page.locator('label', { hasText: labelText }) }).first();
    const input = item.locator('input, textarea').first();
    if (await input.count() > 0 && await input.isVisible()) {
      await input.fill(value);
    }
  } catch (e) {}
}

async function selectDropdownIfExists(page, labelText, optionText) {
  try {
    const item = page.locator('.el-form-item:visible').filter({ has: page.locator('label', { hasText: labelText }) }).first();
    const input = item.locator('.el-select, input').first();
    if (await input.count() > 0 && await input.isVisible()) {
      await input.click({ force: true });
      await page.waitForTimeout(500);
      
      const options = await page.locator('.el-select-dropdown__item:visible').all();
      let clicked = false;
      for (const opt of options) {
         const text = await opt.innerText();
         if (text.includes(optionText)) {
            await opt.click({ force: true });
            clicked = true;
            break;
         }
      }
      if (!clicked && options.length > 0) {
         await options[0].click({ force: true });
         clicked = true;
      }
      if (!clicked) {
         await page.keyboard.press('Escape');
      }
      await page.waitForTimeout(500);
    }
  } catch (e) {}
}

async function submitFlow(page) {
  try {
    const btnSubmit = await page.locator('button:has-text("草稿转正"), button:has-text("提交流转")').first();
    if (await btnSubmit.count() > 0 && await btnSubmit.isVisible()) {
      await btnSubmit.click({ force: true });
    }
    await page.waitForTimeout(1000);
    const btnConfirm = await page.locator('.el-message-box__btns button.el-button--primary').first();
    if (await btnConfirm.count() > 0 && await btnConfirm.isVisible()) {
      await btnConfirm.click({ force: true });
    }
    await page.waitForTimeout(2000);
  } catch (e) {}
}

async function fillDatePickerIfExists(page, labelText, value) {
  try {
    const item = page.locator('.el-form-item:visible').filter({ has: page.locator('label', { hasText: labelText }) }).first();
    const input = item.locator('input').first();
    if (await input.count() > 0 && await input.isVisible()) {
      await input.click({ force: true });
      await page.waitForTimeout(500);
      const todayBtn = page.locator('button:has-text("今天"), button:has-text("此刻"), td.today').filter({ visible: true }).first();
      if (await todayBtn.count() > 0 && await todayBtn.isVisible()) {
         await todayBtn.click({ force: true });
      } else {
         // fallback to forcefully fill and enter
         await input.evaluate((node, val) => {
           node.value = val;
           node.dispatchEvent(new Event('input', { bubbles: true }));
           node.dispatchEvent(new Event('change', { bubbles: true }));
         }, value);
         await page.keyboard.press('Enter');
      }
      await page.waitForTimeout(500);
    }
  } catch (e) {}
}

(async () => {
  const browser = await chromium.launch({ headless: false, slowMo: 500 });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();

  try {
    // Step 1: Admin
    await login(page, context, 'admin');
    await page.goto('http://localhost:5173/case/new');
    await page.waitForTimeout(2000);
    
    // Click the first available manual workflow to create a draft
    await page.locator('article:not(.work-row--subflow) button.work-name').first().click();
    await page.waitForTimeout(3000);

    await takeScreenshot(page, '1_admin_new_case');
    
    // Fill out form
    await fillFormIfExists(page, '标题', '全链路测试案件');
    await fillFormIfExists(page, '案件号', 'TEST-2026-001');
    await fillFormIfExists(page, '委托人', '测试委托人');
    await fillFormIfExists(page, '项目金额', '10000');
    
    // Explicitly fill case_acceptor1 required fields so auto-submit to CLERK_REGISTER passes!
    await fillFormIfExists(page, '快递单号', 'JD123456789');
    await fillDatePickerIfExists(page, '收件日期', '2026-06-25');
    await fillDatePickerIfExists(page, '立案日期', '2026-06-25');
    await fillDatePickerIfExists(page, '确定机构时间', '2026-06-25');
    await selectDropdownIfExists(page, '确定机构方式', '随机抽取');
    await selectDropdownIfExists(page, '鉴定类别', '法医临床');
    await selectDropdownIfExists(page, '项目紧急程度', '普通');
    await selectDropdownIfExists(page, '线上/线下', '线下');
    await fillFormIfExists(page, '承办法人', '测试承办法人');
    await fillFormIfExists(page, '原告/申请人', '原告张三');
    await fillFormIfExists(page, '被告/被申请人', '被告李四');
    await fillFormIfExists(page, '鉴定事项', '测试鉴定事项说明');

    // Try to submit
    await submitFlow(page);
    await page.waitForTimeout(1000);
    await takeScreenshot(page, '1_admin_after_submit');
    
    // Step 2: Case Acceptor
    await login(page, context, 'case_acceptor1');
    await page.goto('http://localhost:5173/my-work');
    await page.waitForTimeout(2000);
    await page.locator('table').locator('text="办理"').first().click();
    await page.waitForTimeout(3000);
    await takeScreenshot(page, '2_case_acceptor_form');
    await submitFlow(page);

    // Step 3: Dept Leader
    await login(page, context, 'dept_leader2');
    await page.goto('http://localhost:5173/my-work');
    await page.waitForTimeout(2000);
    await page.locator('table').locator('text="办理"').first().click();
    await page.waitForTimeout(3000);
    await takeScreenshot(page, '3_dept_leader_form');
    
    // Need to fill boolean/select: 是否受理
    await selectDropdownIfExists(page, '是否受理', '是');
    await selectDropdownIfExists(page, '分配项目负责人', 'project_leader2');
    await selectDropdownIfExists(page, '分配项目辅助人', 'project_assistant2');
    
    await submitFlow(page);

    // Step 4: Project Leader (Payment)
    await login(page, context, 'project_leader2');
    await page.goto('http://localhost:5173/my-work');
    await page.waitForTimeout(2000);
    await page.locator('table').locator('text="办理"').first().click();
    await page.waitForTimeout(3000);
    await takeScreenshot(page, '4_project_leader_payment');
    
    await selectDropdownIfExists(page, '是否需要初步勘验', '否');
    await submitFlow(page);

    // Wait, the workflow might require two steps for project leader (Initial Survey -> Payment)
    // Let's check my-work again to see if there's a second task
    await page.goto('http://localhost:5173/my-work');
    await page.waitForTimeout(2000);
    const count = await page.locator('table tr').count();
    if (count > 1) {
      await page.locator('table').locator('text="办理"').first().click();
      await page.waitForTimeout(3000);
      await takeScreenshot(page, '4_project_leader_payment_confirm');
      await selectDropdownIfExists(page, '确认是否缴费', '是');
      await submitFlow(page);
    }

    // Step 5: Project Assistant (QC Draft)
    await login(page, context, 'project_assistant2');
    await page.goto('http://localhost:5173/my-work');
    await page.waitForTimeout(2000);
    await page.locator('table').locator('text="办理"').first().click();
    await page.waitForTimeout(3000);
    await takeScreenshot(page, '5_project_assistant_qc');
    await submitFlow(page);

    // Step 6: QC Review (Project Leader)
    await login(page, context, 'project_leader2');
    await page.goto('http://localhost:5173/my-work');
    await page.waitForTimeout(2000);
    await page.locator('table').locator('text="办理"').first().click();
    await page.waitForTimeout(3000);
    await takeScreenshot(page, '6_project_leader_qc_review');
    await selectDropdownIfExists(page, '审核结果', '通过');
    await submitFlow(page);

    // We skip the archivist seal if we didn't specify requires seal, but let's assume it goes to Project Assistant for Report Drafting
    await login(page, context, 'project_assistant2');
    await page.goto('http://localhost:5173/my-work');
    await page.waitForTimeout(2000);
    await page.locator('table').locator('text="办理"').first().click();
    await page.waitForTimeout(3000);
    await takeScreenshot(page, '7_project_assistant_report_draft');
    await selectDropdownIfExists(page, '是否满足出具报告条件', '是');
    await selectDropdownIfExists(page, '是否需要出具征求意见稿', '否');
    await submitFlow(page);

    // Report Review (Project Leader)
    await login(page, context, 'project_leader2');
    await page.goto('http://localhost:5173/my-work');
    await page.waitForTimeout(2000);
    await page.locator('table').locator('text="办理"').first().click();
    await page.waitForTimeout(3000);
    await takeScreenshot(page, '8_project_leader_report_review');
    await selectDropdownIfExists(page, '是否通过', '是');
    await submitFlow(page);

    // Dept Leader Report Review
    await login(page, context, 'dept_leader2');
    await page.goto('http://localhost:5173/my-work');
    await page.waitForTimeout(2000);
    await page.locator('table').locator('text="办理"').first().click();
    await page.waitForTimeout(3000);
    await takeScreenshot(page, '8_dept_leader_report_review');
    await selectDropdownIfExists(page, '是否通过', '是');
    await submitFlow(page);

    // Archivist Archive
    await login(page, context, 'archivist');
    await page.goto('http://localhost:5173/my-work');
    await page.waitForTimeout(2000);
    await page.locator('table').locator('text="办理"').first().click();
    await page.waitForTimeout(3000);
    await takeScreenshot(page, '9_archivist_archive');
    await page.click('button:has-text("提交流转")');
    await page.waitForTimeout(2000);

    console.log('All tests finished successfully.');

  } catch (err) {
    console.error('Error during test:', err);
    await takeScreenshot(page, 'error_state');
  } finally {
    await browser.close();
  }
})();
