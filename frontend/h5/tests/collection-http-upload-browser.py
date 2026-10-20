# -*- coding: utf-8 -*-
"""Exercise collection upload and preview on HTTP/HTTPS with isolated business APIs.

Build to frontend/h5/tmp/collection-http-upload/dist, then run this script.
Use --dev to read assets from the existing Vite server on port 10086 instead.
Business APIs are always intercepted; no business data is used or modified.
"""
import json
import struct
import sys
import zlib
from email import policy
from email.parser import BytesParser
from pathlib import Path

from playwright.sync_api import expect, sync_playwright

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "tmp" / "collection-http-upload"
DIST = OUT / "dist"
# A visible non-square fixture catches a collapsed or stretched preview that a 1px image misses.
def png_chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))


PNG = (b"\x89PNG\r\n\x1a\n"
       + png_chunk(b"IHDR", struct.pack(">IIBBBBB", 400, 240, 8, 2, 0, 0, 0))
       + png_chunk(b"IDAT", zlib.compress(b"".join(
           b"\0" + bytes([25, 100 + row // 2, 180]) * 400 for row in range(240))))
       + png_chunk(b"IEND", b""))
DEV = "--dev" in sys.argv
PDF = b"%PDF-1.7\nsynthetic fixture\n%%EOF"
FIELDS = [
    dict(key="name", label="姓名", type="text", group="basic", required=True, enabled=True),
    dict(key="portrait", label="证件照片", type="images", group="materials",
         required=False, enabled=True, maxImages=9, maxSizeMb=1, allowedExtensions=["png"]),
    dict(key="education_report", label="学历认证报备", type="images", group="materials",
         required=False, enabled=True, maxImages=9, maxSizeMb=1, allowedExtensions=["pdf"]),
]


def scenario(browser, origin, width, no_crypto=False):
    context = browser.new_context(viewport={"width": width, "height": 1000},
                                  accept_downloads=True, has_touch=True)
    if no_crypto:
        context.add_init_script("Object.defineProperty(globalThis,'crypto',{value:undefined})")
    page = context.new_page()
    errors, uploads, submitted, held = [], [], [], []
    state = {"images": [], "contents": {}, "fail": False, "hold": True, "next_id": 100}
    page.on("pageerror", lambda error: errors.append(str(error)))

    def reply(route, data, code=0):
        route.fulfill(content_type="application/json; charset=utf-8",
                      body=json.dumps({"code": code, "data": data,
                                       "msg": "合成上传失败，请重试" if code else ""}))

    def request(route):
        req = route.request
        path = req.url.split(origin, 1)[-1].split("?", 1)[0]
        if path.startswith("/public-api/"):
            assert req.headers.get("x-student-info-token") == "a" * 43
            assert not req.headers.get("authorization")
            if path.endswith("/detail"):
                return reply(route, dict(status="SUBMITTED" if submitted else "DRAFT",
                    tenantId=1, fields=FIELDS, options={}, images=state["images"]))
            if path.endswith("/images"):
                if state["fail"]:
                    state["fail"] = False
                    return reply(route, None, 500)
                message = BytesParser(policy=policy.default).parsebytes(
                    ("Content-Type: " + req.headers["content-type"] + "\r\n\r\n").encode()
                    + req.post_data_buffer)
                parts = {part.get_param("name", header="content-disposition"): part
                         for part in message.iter_parts()}
                field = parts["fieldKey"].get_content().strip()
                side = parts["side"].get_content().strip()
                file = parts["file"]
                content = file.get_payload(decode=True)
                assert side == "" and field in ["portrait", "education_report"]
                assert content == (PNG if field == "portrait" else PDF)
                image = dict(id=state["next_id"], fieldKey=field, side=side,
                             contentType="image/png" if field == "portrait" else "application/pdf",
                             name=file.get_filename(), size=len(content))
                state["next_id"] += 1
                state["images"].append(image)
                state["contents"][image["id"]] = content
                uploads.append(image)
                if state["hold"]:
                    state["hold"] = False
                    held.append((route, image))
                    return
                return reply(route, image)
            if "/images/" in path:
                image_id = int(path.rsplit("/", 1)[-1])
                if req.method == "DELETE":
                    state["images"] = [image for image in state["images"] if image["id"] != image_id]
                    return reply(route, True)
                return route.fulfill(content_type="application/octet-stream",
                                     body=state["contents"][image_id])
            if path.endswith("/submit"):
                submitted.append(req.post_data_json)
                return reply(route, True)
            raise AssertionError("Unexpected public API")
        if path.startswith(("/app-api/", "/part-api/")):
            return reply(route, {})
        if DEV:
            response = route.fetch(url="http://127.0.0.1:10086" + req.url[len(origin):])
            return route.fulfill(response=response)
        asset = DIST / path.lstrip("/")
        if not asset.is_file():
            asset = DIST / "index.html"
        types = {".html": "text/html", ".js": "text/javascript", ".css": "text/css",
                 ".json": "application/json", ".png": "image/png", ".svg": "image/svg+xml"}
        route.fulfill(path=str(asset), content_type=types.get(asset.suffix, "application/octet-stream"))

    page.route("**/*", request)
    page.goto(origin + "/student-info-form#token=" + "a" * 43)
    try:
        expect(page.locator("#name")).to_be_visible()
    except AssertionError:
        print({"page_errors": errors})
        raise
    assert page.evaluate("isSecureContext") == origin.startswith("https:")
    if not origin.startswith("https:") and not no_crypto:
        assert page.evaluate("typeof crypto.randomUUID") == "undefined"
        assert page.evaluate("typeof crypto.getRandomValues") == "function"
    page.locator("#name").fill("合成测试")
    page.locator("[data-group=materials] summary").click()
    submit = page.get_by_role("button", name="提交信息", exact=True)

    def upload(label, name, content, mime):
        page.get_by_label(label + "添加附件", exact=True).set_input_files(
            {"name": name, "buffer": content, "mimeType": mime})

    upload("证件照片", "portrait.png", PNG, "image/png")
    expect(page.locator(".collection-images__pending")).to_be_visible()
    expect(submit).to_be_disabled()
    assert len(held) == 1
    route, image = held.pop()
    reply(route, image)
    preview = page.get_by_role("button", name="预览证件照片", exact=True)
    expect(preview.locator("img")).to_be_visible()
    assert preview.locator("img").evaluate("(img)=>img.complete && img.naturalWidth>0")
    expect(submit).to_be_enabled()
    preview.click()
    expect(page.locator(".van-image-preview")).to_be_visible()
    expect(page.locator(".van-image-preview")).to_have_css("opacity", "1")
    expect(page.locator(".van-image-preview__overlay")).to_have_css("opacity", "1")
    expect(page.locator(".van-image-preview__overlay")).to_have_css("background-color", "rgba(0, 0, 0, 0.9)")
    page.wait_for_function("""() => {
        const image = document.querySelector('.van-image-preview img');
        return image?.complete && image.naturalWidth === 400;
    }""")
    geometry = page.locator(".van-image-preview").evaluate("""root => {
        const image = root.querySelector('img');
        const rect = node => { const r=node.getBoundingClientRect();
            return {x:r.x,y:r.y,width:r.width,height:r.height}; };
        const style=getComputedStyle(root);
        return {root:rect(root),image:rect(image),background:style.backgroundColor,
            backdrop:style.backdropFilter,viewport:{width:innerWidth,height:innerHeight}};
    }""")
    prefix = ("dev" if DEV else "bundle") + ("-https" if origin.startswith("https:") else "-http")
    if no_crypto:
        prefix += "-no-crypto"
    page.screenshot(path=str(OUT / f"{prefix}-preview-{width}.png"))
    print(json.dumps({"preview":prefix,"width":width,"geometry":geometry}))
    assert abs(geometry["root"]["width"] - width) <= 2, geometry
    assert abs(geometry["root"]["height"] - 1000) <= 2, geometry
    assert abs(geometry["root"]["x"]) <= 1 and abs(geometry["root"]["y"]) <= 1, geometry
    assert geometry["image"]["width"] >= min(width, 400) * 0.8, geometry
    assert abs(geometry["image"]["width"] / geometry["image"]["height"] - 400 / 240) < 0.02, geometry
    assert geometry["image"]["x"] >= -1 and geometry["image"]["y"] >= -1, geometry
    assert geometry["image"]["x"] + geometry["image"]["width"] <= width + 1, geometry
    assert geometry["image"]["y"] + geometry["image"]["height"] <= 1001, geometry
    assert geometry["background"] == "rgba(0, 0, 0, 0)", geometry
    assert geometry["backdrop"] == "none", geometry
    close = page.locator(".van-image-preview__close-icon")
    expect(close).to_be_visible()
    close.click()
    expect(page.locator(".van-image-preview")).to_be_hidden()
    preview.click()
    expect(page.locator(".van-image-preview")).to_be_visible()
    page.locator(".van-image-preview__swipe-item").tap(position={"x": 10, "y": 10})
    expect(page.locator(".van-image-preview")).to_be_hidden()

    upload("学历认证报备", "report.pdf", PDF, "application/pdf")
    document = page.get_by_role("button", name="预览学历认证报备", exact=True)
    expect(document).to_contain_text("report.pdf")
    with page.expect_download() as event:
        document.click()
    assert event.value.suggested_filename == "report.pdf"
    assert Path(event.value.path()).read_bytes() == PDF
    page.get_by_role("button", name="删除学历认证报备添加附件", exact=True).click()
    expect(document).to_have_count(0)

    state["fail"] = True
    upload("证件照片", "retry.png", PNG, "image/png")
    retry = page.get_by_role("button", name="重试", exact=True)
    expect(retry).to_be_visible()
    expect(submit).to_be_disabled()
    retry.click()
    expect(preview).to_have_count(2)
    expect(submit).to_be_enabled()
    expect(preview.locator("img")).to_have_count(2)

    count = len(uploads)
    upload("证件照片", "wrong.pdf", PDF, "application/pdf")
    expect(page.locator(".van-toast")).to_contain_text("附件格式不符合此字段要求")
    upload("证件照片", "large.png", b"x" * (1024 * 1024 + 1), "image/png")
    expect(page.locator(".van-toast")).to_contain_text("单个不能超过")
    assert len(uploads) == count
    assert not page.evaluate("document.documentElement.scrollWidth > innerWidth")
    label = ("https" if origin.startswith("https:") else "http") + ("-no-crypto" if no_crypto else "")
    page.screenshot(path=str(OUT / f"{prefix}-{width}.png"), full_page=True)
    submit.click()
    page.get_by_role("button", name="确认", exact=True).click()
    expect(page.locator(".van-empty")).to_contain_text("信息已提交")
    assert len(submitted) == 1
    assert submitted[0]["values"]["portrait"] == [image["id"] for image in state["images"]]
    assert submitted[0]["values"]["education_report"] == []
    assert not errors, errors
    context.close()
    print(f"PASS {label} {width}px: upload/progress/image/PDF/retry/delete/validation/submission")


if __name__ == "__main__":
    assert (DIST / "index.html").is_file(), "Build the task-owned production bundle first"
    OUT.mkdir(parents=True, exist_ok=True)
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch()
        for origin, width, no_crypto in [
            ("http://192.0.2.123", 1440, False),
            ("http://192.0.2.123", 390, False),
            ("http://192.0.2.123", 320, False),
            ("https://h5.example.test", 390, False),
            ("http://192.0.2.123", 390, True),
        ]:
            scenario(browser, origin, width, no_crypto)
        browser.close()
