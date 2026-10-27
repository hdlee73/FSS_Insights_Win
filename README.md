# FSS Insights for Windows (뉴스 및 연구자료 검색)

국내 기사를 빠르게 모아 시간순으로 확인하고 공유하는 Windows 데스크톱 프로그램입니다. Android 앱 **FSS Insights** 와 화면·기능·동작이 같도록 Android Jetpack Compose 소스를 **Compose Multiplatform Desktop**(Kotlin/JVM)으로 옮겨 만들었습니다. 버전과 데이터 형식이 Android 앱(v2.0.4, versionCode 57)과 맞춰져 있습니다.

- Android 앱 저장소: <https://github.com/hdlee73/FSS_insights>
- 이 저장소(Windows): <https://github.com/hdlee73/FSS_Insights_Win>

## 설치

1. 오른쪽 **Releases** 에서 최신 버전의 `fss-insights-windows-vX.Y.Z.msi`(일반 PC) 또는 `fss-insights-windows-arm64-vX.Y.Z.msi`(ARM 기반 PC)를 내려받습니다.
2. 내려받은 `.msi` 를 두 번 눌러 설치합니다. 관리자 권한은 필요하지 않습니다(현재 사용자 설치).
3. 시작 메뉴 또는 바탕 화면의 **FSS Insights** 를 실행합니다.

앱은 Java가 함께 들어 있는 독립 실행 패키지이므로 별도로 Java를 설치할 필요가 없습니다.

### 처음 실행에 대해

Windows SmartScreen이 "PC를 보호했습니다" 창을 띄울 수 있습니다. **추가 정보 → 실행** 을 누르면 됩니다(공개 시험용으로 서명하지 않은 설치 파일이기 때문입니다).

## 주요 기능

- 최대 10개 키워드를 저장하고 첫 화면(오늘의 브리핑)에서 통합 피드로 확인
- 키워드·회사명 직접 검색
- `AND`, `OR`, 괄호, 따옴표 문구 검색과 공백 AND 검색, `-단어` 제외 검색(예: `금감원 -연예`)
- Google 뉴스와 네이버 뉴스 API 결과를 합치는 통합 검색
- 30대 주요 언론 또는 전체 언론 범위 선택
- 24시간·7일·30일 기간 필터와 날짜별 타임라인
- 언론사, 게시 시각, 제목, 요약 표시
- 별도 창에서 원문 보기, 기사 링크 복사, 원문 PDF 저장, 즐겨찾기
- 기사 제목 전체를 기준으로 한 보수적인 중복 제거
- 검색 결과에서 수집·표시·중복 제외·언론 범위 제외 건수를 구분해 표시
- 네이버 검색 사용 시 100건씩 최대 1,000번째 결과까지 추가 로딩
- 새 버전 알림(트레이 풍선)과 앱 안에서 바로 내려받아 설치

## 스마트 법령검색 (v2.0)

서랍 메뉴의 **스마트 법령검색**에서 `자본시장법 제12조`처럼 입력하면 그 조문이 위임한 시행령·시행규칙·감독규정·시행세칙을 항·호 바로 아래에 보여 줍니다. 위임 문구 없이 인용만 한 규정은 맨 아래 "인용된 법규"로 따로 모으고, 비교표 보기와 PDF 저장(Edge/Chrome 인쇄 기능 사용)을 지원합니다. 법령 서버 주소는 뉴스 프록시(`/law`)를 함께 씁니다.

## 핵심 내용 요약

보도자료·금융관련 리포트·유권해석·비조치의견서·금감원/금융위 행정지도 목록에는 본문에서 핵심 문장을 골라 만든 요약이 제목 아래에 표시됩니다. 안드로이드의 기기 내장 AI(Gemini Nano)와 달리 윈도우는 내장 추출 요약을 쓰므로 별도 설치나 키가 필요 없고 가볍습니다.

## 화면 이동

상단 ☰ 버튼으로 서랍 메뉴를 열어 **오늘의 브리핑 · 뉴스 검색 · 금융시장 동향 · 보도자료 · 금융관련 리포트 · 참고 자료실 · 주요사이트 · 알림 · 앱 정보** 로 이동합니다. Android 앱과 같은 순서·같은 구성입니다. 주요사이트는 사이트마다 간략한 설명과 함께 추가·수정·삭제할 수 있습니다.

- 금융당국 보도자료: 금융감독원·금융위원회 최근 20건 + 제목 검색
- 금융회사 제재공시: 금감원 제재공시 목록 최근 20건을 읽어 제목+내용으로 검색합니다.
- 유권해석·비조치의견서·행정지도: 금융위 법령해석·비조치의견서, 금감원·금융위 행정지도, 제재공시를 각 최근 20건 읽고, 항목을 누르면 해당 게시글로 연결합니다.
- 연구소 최근자료: 자본시장연구원·한국금융연구원·IOSCO 최근 20건
- 목록은 각 기관 웹사이트(IOSCO는 RSS)를 읽어 구성하며, 주소·링크 패턴은 `data/AgencyRepository.kt` 의 `AgencySources` 에 모여 있어 사이트 개편 시 이 파일만 고치면 됩니다.

## 검색 방식

Android 앱과 같은 세 가지 방식을 지원합니다.

1. **통합 검색**: 기본 방식입니다. Google 뉴스와 네이버 뉴스 API 결과를 합칩니다. 네이버 키가 없으면 Google 뉴스만 사용하고 앱은 정상 작동합니다.
2. **바로 검색**: 별도 키 없이 Google 뉴스 RSS를 사용합니다.
3. **네이버 심층 검색**: 사용자가 발급받은 Naver Client ID와 Client Secret을 앱 설정에 입력합니다. 공식 뉴스 검색 API의 `start` 페이지를 이동해 한 번의 100건 제한을 넘어 최대 1,000건까지 확인합니다.

Boolean 검색은 OR 분기를 각각 별도 검색으로 실행한 뒤 결과를 합칩니다. 예를 들어 `금감원 AND (증권사 OR 자산운용사)` 는 `금감원 증권사` 와 `금감원 자산운용사` 를 각각 검색합니다. 공백은 AND로 처리하며 `"내부 통제"` 처럼 따옴표로 문구를 묶을 수 있습니다.

API 키는 소스와 설치 파일에 포함되지 않습니다. 앱에서 입력한 값은 AES-GCM으로 암호화해 이 PC에만 저장합니다.

## 원문 PDF

기사와 공유 아이콘을 누르면 원문을 별도 창에서 열고 **PDF 저장** 을 선택할 수 있습니다. PDF는 Microsoft Edge(없으면 Google Chrome)의 헤드리스 인쇄 기능으로 만들어 화면 밖 내용도 여러 페이지로 저장합니다. 검색 요약을 원문으로 대체하지 않습니다. 로그인·유료 제한과 사이트의 인쇄 스타일은 그대로 적용됩니다.

Edge/Chrome이 없으면 원문 창은 안내 문구를 보여 주고, **원문 열기** 로 기본 브라우저를 대신 띄웁니다.

## 데이터 보관 위치

| 항목 | 위치 |
| --- | --- |
| 설정·즐겨찾기·저장 키워드 | `%APPDATA%\FSS_Insights` |
| Naver 키 암호화 키 | `%APPDATA%\FSS_Insights\credential.key` |
| PDF 저장 | 사용자가 선택한 폴더(기본: 내 문서) |

## 개발 및 빌드

필요 환경:

- JDK 21
- Gradle wrapper 포함(별도 설치 불필요)
- MSI 패키징에는 WiX Toolset v3 (`candle.exe`, `light.exe`) 필요. `.exe`/폴더 배포판은 WiX 없이도 만들 수 있습니다.

```bash
./gradlew.bat test              # 단위 테스트(Android 앱과 같은 파서 테스트 97개)
./gradlew.bat run               # 개발 실행
./gradlew.bat createDistributable   # 실행 폴더형 배포판
./gradlew.bat packageMsi            # Windows 설치 파일(.msi)
```

- 실행 폴더형 배포판: `build/compose/binaries/main/app/FSS Insights/`
- 설치 파일: `build/compose/binaries/main/msi/`

GitHub Actions(`.github/workflows/windows.yml`)는 `windows` 러너에서 단위 테스트를 통과한 뒤 `.msi` 를 만들어 GitHub 릴리스에 게시합니다.

### 뉴스 프록시 설정(선택)

네이버 뉴스 검색을 앱에 내장한 프록시 서버로 처리하려면 빌드할 때 `NEWS_PROXY_URL`, `NEWS_PROXY_TOKEN` 환경변수(또는 `-PnewsProxyUrl=... -PnewsProxyToken=...`)를 넘깁니다. 값은 `build-config.properties` 로 앱에 함께 실려 들어가고, 비워 두면 네이버 심층 검색은 사용자가 직접 입력한 키로만 동작합니다.

Android 저장소와 같이 저장소 **Variables**(`NEWS_PROXY_URL`)와 **Secrets**(`NEWS_PROXY_TOKEN`)에 값을 넣으면 워크플로가 그대로 사용합니다. 프록시 서버(Cloudflare Worker) 소스는 Android 저장소의 `server/news-proxy/` 에 있습니다.

## Android 앱과 다른 점(같은 기능, 데스크톱 대역)

| Android | Windows |
| --- | --- |
| `SharedPreferences` | `%APPDATA%\FSS_Insights` 의 설정 파일 |
| Android Keystore AES-GCM | `credential.key` 파일 기반 AES-GCM |
| WebView 인쇄로 PDF | Edge/Chrome 헤드리스 인쇄로 PDF |
| WorkManager 주기 작업 | 앱 내부 스케줄러(코루틴) |
| 시스템 알림 | 트레이 풍선 알림 |
| APK 자동 업데이트 | GitHub 릴리스의 `.msi` 내려받아 실행 |

## 데이터 제공 관련

앱은 기사 제목·요약·링크만 표시하고 기사 본문을 복제하지 않습니다. 실제 서비스 운영 시 Google 뉴스 및 네이버 API의 최신 이용 조건과 각 언론사의 링크·콘텐츠 정책을 확인하세요.

## 라이선스

MIT
