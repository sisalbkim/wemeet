import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright";

const baseUrl = process.env.WEMEET_BASE_URL || "http://localhost:8080";
const outputDir = path.resolve("artifacts", "ppt_screenshots");

const viewport = { width: 1440, height: 1024 };

async function ensureDir(dir) {
  await fs.mkdir(dir, { recursive: true });
}

async function capture(page, name, url) {
  await page.goto(url, { waitUntil: "networkidle" });
  await page.screenshot({ path: path.join(outputDir, name), fullPage: false });
}

async function textOf(locator) {
  const text = await locator.textContent();
  return (text || "").trim();
}

async function main() {
  await ensureDir(outputDir);

  const browser = await chromium.launch({
    channel: "msedge",
    headless: true
  });

  const context = await browser.newContext({
    viewport,
    deviceScaleFactor: 1
  });

  const page = await context.newPage();
  page.setDefaultTimeout(15000);

  const stamp = Date.now();
  const email = `ppt-${stamp}@example.com`;
  const loginId = `pptuser${stamp}`;
  const password = "Passw0rd!";
  const nickname = "PPT테스터";
  const baseAddress = "서울특별시 중구 명동길 74";

  step("capture guest plan");
  await capture(page, "guest-plan.png", `${baseUrl}/guest/plan`);
  step("capture guest results");
  await capture(
    page,
    "guest-results.png",
    `${baseUrl}/search/results?guest=true&guestAddress=${encodeURIComponent(baseAddress)}&category=${encodeURIComponent("맛집")}&mode=CENTER`
  );

  step("open signup");
  await page.goto(`${baseUrl}/signup`, { waitUntil: "domcontentloaded" });
  await page.fill("#email", email);
  step("email duplicate check");
  await page.getByRole("button", { name: "중복확인" }).click();
  step("email send");
  await page.getByRole("button", { name: "이메일 전송" }).click();

  const verifyFeedback = page.locator("[data-email-verify-feedback]");
  await page.waitForTimeout(1000);
  const feedbackText = await textOf(verifyFeedback);
  const codeMatch = feedbackText.match(/인증코드:\s*(\d{6})/);
  if (!codeMatch) {
    throw new Error(`인증코드 추출 실패: ${feedbackText}`);
  }

  step("email verify");
  await page.fill("#emailVerificationCode", codeMatch[1]);
  await page.getByRole("button", { name: "인증 확인" }).click();
  step("fill signup profile");
  await page.fill("#nickname", nickname);
  await page.fill("#signupId", loginId);
  await page.fill("#signupPassword", password);
  await page.fill("#confirmPassword", password);
  await page.fill("#baseAddress", baseAddress);
  step("submit signup");
  await page.getByRole("button", { name: "회원가입 완료" }).click();
  await page.waitForURL(/\/login/);

  step("login");
  await page.fill("#loginId", loginId);
  await page.fill("#password", password);
  await page.getByRole("button", { name: "로그인" }).click();
  await page.waitForURL(`${baseUrl}/`);

  step("capture home member");
  await capture(page, "home-member.png", `${baseUrl}/`);
  step("capture friends");
  await capture(page, "friends.png", `${baseUrl}/friends`);
  step("capture history");
  await capture(page, "history.png", `${baseUrl}/history`);
  step("capture profile");
  await capture(page, "profile.png", `${baseUrl}/profile`);
  step("capture meeting form");
  await capture(page, "meeting-form.png", `${baseUrl}/meetings/new`);
  step("capture member results");
  await capture(
    page,
    "member-results.png",
    `${baseUrl}/search/results?category=${encodeURIComponent("맛집")}&mode=CENTER`
  );

  await browser.close();

  console.log(JSON.stringify({
    email,
    loginId,
    outputDir
  }, null, 2));
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
  const step = (message) => console.log(`[capture] ${message}`);
