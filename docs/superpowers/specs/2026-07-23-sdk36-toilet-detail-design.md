# Design Spec: SDK 36 Upgrade + Toilet Detail Bottom Sheet

**Date:** 2026-07-23  
**Scope:** SDK 36 업그레이드 (deprecated API 교체) + 화장실 상세 정보 Bottom Sheet 추가  
**Approach:** B안 — 실용적 현대화 (MainActivity 구조 유지, 코루틴 도입)

---

## 1. SDK 36 업그레이드

### build.gradle (app)
- `compileSdk` 35 → 36
- `targetSdkVersion` 35 → 36
- `buildToolsVersion "29.0.3"` 삭제 (AGP 자동 결정)

### build.gradle (root)
- `jcenter()` → `mavenCentral()` (repositories 블록 2곳 모두)

### deprecated API 교체 (MainActivity.kt)

| 현재 코드 | 교체 대상 | 비고 |
|-----------|-----------|------|
| `AsyncTask<Void, JSONArray, String>` | `lifecycleScope.launch` + `Dispatchers.IO` | `ToiletReadTask` inner class 제거 |
| `ProgressDialog` | `CircularProgressIndicator` (XML에 추가) | `View.VISIBLE` / `View.GONE` 토글 |
| `cm.activeNetworkInfo` / `NetworkInfo` | `cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NET_CAPABILITY_INTERNET)` | `ConnectivityManager` 재사용 |
| `resources.getDrawable(R.drawable.toilet_sign)` | `ContextCompat.getDrawable(this, R.drawable.toilet_sign)` | |

### 코루틴 Job 관리
- `var fetchJob: Job? = null` — MainActivity 멤버 변수로 선언
- `onStart()`: `fetchJob = lifecycleScope.launch { fetchToilets() }`
- `onStop()`: `fetchJob?.cancel()`
- 페이지별 중간 UI 업데이트는 `withContext(Dispatchers.Main) { addMarkers(...) }` 로 처리

---

## 2. 화장실 상세 정보 Bottom Sheet

### 표시 항목
서울 열린 데이터 API(`mgisToiletPoi`) 필드 기준:

| 항목 | API 필드 | 없을 때 |
|------|----------|---------|
| 화장실 이름 | `CONTS_NAME` | 항상 존재 |
| 주소 | `RDNMADR` (도로명), 없으면 `LNMADR` | 행 숨김 |
| 운영시간 | `OPNTIME` ~ `CLSTIME` | 행 숨김 |
| 장애인 시설 | `DSBLED_TOILET_YN` ("Y"/"N") | 행 숨김 |
| 기저귀 교환대 | `BABY_POTY_YN` ("Y"/"N") | 행 숨김 |

### 레이아웃: `res/layout/bottom_sheet_toilet_detail.xml`
- `ConstraintLayout` 루트, `app:layout_behavior="BottomSheetBehavior"`
- 드래그 핸들 (`View`, 4dp 높이)
- 각 항목: `TextView` 쌍 (라벨 + 값), 없으면 `visibility="GONE"`

### activity_main.xml 변경
- Bottom Sheet 레이아웃을 `<include>`로 포함
- `BottomSheetBehavior.STATE_HIDDEN`이 기본 상태

### MainActivity.kt 변경

**데이터 브릿지 추가:**
```kotlin
val reverseItemMap = mutableMapOf<MyItem, JSONObject>()
```
`addMarkers()`에서 `itemMap`에 넣을 때 `reverseItemMap`에도 동시에 저장.

**클릭 리스너:**
```kotlin
clusterManager.setOnClusterItemClickListener { item ->
    reverseItemMap[item]?.let { showToiletDetail(it) }
    true
}
```

**`showToiletDetail(json: JSONObject)`:**
- Bottom Sheet 뷰 바인딩으로 각 필드 채움
- 빈 값이면 해당 행 `View.GONE`
- `BottomSheetBehavior.STATE_EXPANDED`로 상태 변경

### MyItem 변경 없음
아이콘·위치·이름만 담는 현재 구조 유지. 원본 JSON은 `reverseItemMap`에서 참조.

---

## 3. 변경 파일 목록

| 파일 | 변경 유형 |
|------|-----------|
| `build.gradle` (root) | `jcenter()` → `mavenCentral()` |
| `app/build.gradle` | SDK 버전, `buildToolsVersion` 삭제 |
| `app/src/main/java/.../MainActivity.kt` | deprecated API 교체, 코루틴 도입, Bottom Sheet 로직 |
| `app/src/main/res/layout/activity_main.xml` | `CircularProgressIndicator`, Bottom Sheet include 추가 |
| `app/src/main/res/layout/bottom_sheet_toilet_detail.xml` | 신규 파일 |
