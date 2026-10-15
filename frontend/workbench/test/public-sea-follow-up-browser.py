# -*- coding: utf-8 -*-
"""Real calendar component, synthetic A/B responses; no business writes or shared browser profile."""
import datetime as dt
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect

day = dt.date.today().isoformat()
stamp = int(dt.datetime.now().timestamp() * 1000)
state = {"viewer": "A", "pool": "assigned"}
errors = []

def handler(route):
    path = urlparse(route.request.url).path
    actionable = state["viewer"] == "B" and state["pool"] == "assigned"
    data = []
    lead = dict(id=1, leadNo="LD-PUBLIC-SEA-FIXTURE", submittedName="公海跟进验证",
                status="valid", assignmentStatus="owned", ownerUserId=10,
                leadCategoryLabelSnapshot="测试分类", overviewVisible=True, visibleTabs=["overview"],
                availableActions=[dict(code="ADD_FOLLOW_UP", enabled=True)] if actionable else [],
                attachments=[], intendedProducts=[])
    if path.endswith("/lead-follow-up-calendar/days"):
        data = [dict(date=day, count=1)] if actionable else []
    elif path.endswith("/lead-follow-up-calendar/cards"):
        data = dict(total=1 if actionable else 0, list=[dict(lead=lead, deadline=stamp,
                    canReadFollowUp=False, lastFollowUp=None)] if actionable else [])
    elif path.endswith("/lead/get"):
        data = lead
    route.fulfill(json=dict(code=0, data=data))

with sync_playwright() as p:
    browser = p.chromium.launch(channel="chrome", headless=True)
    for width in (1600, 390):
        context = browser.new_context(viewport={"width": width, "height": 1000})
        page = context.new_page()
        page.on("pageerror", lambda error: errors.append(str(error)))
        page.route("**/admin-api/**", handler)
        for viewer, pool in (("A", "assigned"), ("A", "waiting_assignment"),
                             ("B", "waiting_assignment"), ("B", "assigned")):
            state.update(viewer=viewer, pool=pool)
            page.goto("http://127.0.0.1:5174/test/lead-calendar.html")
            if viewer == "B" and pool == "assigned":
                expect(page.locator(".lead-calendar-count")).to_have_text("1")
                page.locator(".lead-calendar-count").click()
                expect(page.get_by_text("公海跟进验证", exact=True)).to_be_visible()
                expect(page.get_by_role("button", name="填写跟进记录", exact=True)).to_be_enabled()
            else:
                expect(page.get_by_text("当前日历范围暂无待跟进客资", exact=True)).to_be_visible()
                expect(page.locator(".lead-calendar-count")).to_have_count(0)
            print(f"PASS width={width} viewer={viewer} pool={pool}")
        context.close()
    browser.close()
assert not errors, errors
