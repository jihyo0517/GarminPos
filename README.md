# GarminPos
Garmin POS Mobile 앱에 찍은 바코드를 긴빠이 해와서 재고 관리 편하게 하기 위한 프로젝트

## 구성

| 프로그램 | 위치 | 역할 |
|---|---|---|
| **garminpos-app** | 태블릿 (Android, 접근성 서비스) | USB 스캐너의 키 입력을 가로채 SN 조립 → 노트북으로 전송. 키는 그대로 통과시켜 POS 입력에 영향 없음 |
| **sn-server** | 노트북 (Java Swing) | HTTP로 SN 수신 → 표에 표시, 행 클릭 시 클립보드 복사 |

```
[USB 스캐너] --HID 키입력--> [태블릿: garminpos-app] --HTTP POST--> [노트북: sn-server]
                                      |                                    ^
                                      +------- UDP 브로드캐스트 탐색 -------+
                                             (노트북 IP 바뀌면 자동 복구)
```

## 두 프로그램의 통신

### 1. 스캔 전송 — HTTP

```
POST http://<노트북IP>:8000/scan
Content-Type: application/x-www-form-urlencoded

sn=<바코드>&ts=<epoch millis>&device=<기기명>
```

| 응답 | 의미 |
|---|---|
| `200 OK` | 수신 완료 (표에 추가됨) |
| `400 missing sn` / `bad request` | sn 파라미터 없음 / 본문 파싱 실패 |
| `405 POST only` | POST 이외의 메서드 |
| `413 body too large` | 본문 8KB 초과 |

- 전송은 태블릿 쪽 단일 스레드 풀에서 순차 처리 (스캔 순서 보장)
- 실패 시 **최대 5회 재시도**, 지수 백오프 (500ms → 1s → 2s → 4s)
- 5회 모두 실패하면 해당 SN은 버려지고 태블릿 화면에 Toast로 유실 알림
- 같은 SN을 2초(설정 가능) 내에 다시 스캔하면 중복으로 간주하고 전송 안 함

### 2. 수신기 자동 탐색 — UDP (IP 변경 대응)

노트북 IP는 DHCP라 바뀔 수 있음(실제 1.2→1.3→1.5 이력). 첫 전송 실패 시 태블릿이 브로드캐스트로 수신기를 다시 찾는다.

```
태블릿                                          노트북(sn-server)
  |                                                  |
  |-- UDP broadcast :8000  "SN_DISCOVER_V1" ------->|  (daemon 스레드가 상시 대기)
  |<------------- "SN_SERVER_V1" 응답 --------------|
  |                                                  |
  응답 패킷의 발신 IP를 새 서버 주소로 저장(Prefs) 후 재시도
```

- 탐색 타임아웃 1초, 못 찾으면 기존 IP 유지한 채 재시도 계속
- 방화벽: `snserver.exe` 인바운드 TCP/UDP 허용 규칙 필요 (현재 등록돼 있음)

### 스캔 1건의 일생

```
스캐너 키입력 → ScanKeyService.onKeyEvent   (키 필터, 원본 키는 통과)
             → ScanAssembler.onChar         (문자 누적, Enter 에서 SN 확정)
             → Debouncer.shouldSend         (2초 중복 억제)
             → ScanSender.send              (백그라운드 전송 + 재시도/재탐색)
             → HTTP POST /scan
             → SnServer.handleScan          (검증/파싱)
             → SnServer.appendRow           (EDT 에서 표에 추가, 자동 스크롤)
```

## 주요 메서드

### garminpos-app (태블릿)

**`ScanKeyService`** — 접근성 서비스 본체 (모든 콜백은 메인 스레드)
| 메서드 | 역할 |
|---|---|
| `onServiceConnected()` | 서비스 활성화 시 설정 적용 + 설정 변경 리스너 등록 |
| `applyPrefs()` | Prefs 를 읽어 Assembler/Debouncer/Sender 재생성. 설정 변경 시 실시간 반영 |
| `onKeyEvent(event): Boolean` | 스캐너 키 캡처. Enter→`'\n'`, 그 외는 `unicodeChar`. **항상 `false` 반환** → 키가 POS 앱으로 그대로 전달됨 |
| `onDestroy()` | 리스너 해제 + sender 정리 |

**`ScanAssembler`** — 키 스트림을 SN 문자열로 조립
| 메서드 | 역할 |
|---|---|
| `onChar(c, now): String?` | 문자 누적. 직전 키와 간격이 `burstGapMillis`(기본 100ms) 초과면 버퍼 리셋(사람 타이핑 배제). Enter 에서 조립된 SN 반환 |

**`Debouncer`** — 중복 스캔 억제
| 메서드 | 역할 |
|---|---|
| `shouldSend(sn, now): Boolean` | 같은 SN 이 `windowMillis`(기본 2초) 내 재등장하면 `false`. 기록 64개 초과 시 만료분 자동 정리 |

**`ScanSender`** — 전송 + 재시도 + 재탐색 (단일 스레드 풀)
| 메서드 | 역할 |
|---|---|
| `send(sn, ts)` | 전송 작업을 풀에 큐잉 (호출 스레드 비차단) |
| `trySend(sn, ts)` | 5회 재시도 루프. **1회차 실패 직후 `rediscover()`** 로 IP 갱신 시도. 전부 실패 시 `onDrop(sn)` 콜백 |
| `postOnce(body): Boolean` | 실제 HTTP POST 1회 (connect/read 타임아웃 2초). 매 호출마다 `locator.baseUrl` 을 다시 읽으므로 IP 갱신이 즉시 반영됨 |
| `rediscover()` | `discover()` 로 서버 IP 탐색, 찾으면 `locator.updateIp()` 에 저장 |
| `close()` | 풀 종료 (큐에 남은 전송은 마저 수행) |

**`Discovery`** — UDP 탐색 클라이언트
| 메서드 | 역할 |
|---|---|
| `discover(port, timeoutMs=1000, target=255.255.255.255): String?` | `SN_DISCOVER_V1` 브로드캐스트 → `SN_SERVER_V1` 응답의 발신 IP 반환. 타임아웃/오류 시 `null` |

**`Prefs`** (implements `ServerLocator`) — 설정 저장소
| 멤버 | 역할 |
|---|---|
| `laptopIp` / `port` / `device` / `debounceMillis` / `burstGapMillis` | SharedPreferences 게터/세터 |
| `baseUrl` | `http://<laptopIp>:<port>` |
| `updateIp(ip)` | 자동 탐색 결과 저장 → 설정 변경 리스너를 타고 서비스에 반영 |

**`SettingsActivity`** — IP/포트/기기명 편집, `[테스트]` 버튼(PING 전송), 접근성 설정 바로가기

### sn-server (노트북)

**`SnServer`** — HTTP 수신 + Swing UI (단일 파일)
| 메서드 | 역할 |
|---|---|
| `main(args)` | HTTP 요청 타임아웃(10초) 설정 → 미처리 예외/종료(shutdown hook) 로깅 등록 → `HttpServer` 기동(포트 8000, 스레드 풀 2) → UDP 탐색 응답기 시작 → UI. 포트 충돌 시 오류 대화상자 후 종료. 테스트용 포트 변경: `-Dsnserver.port=NNNN` |
| `handleScan(ex)` | `/scan` 핸들러. POST 검증(405) → 본문 8KB 제한(413) → `sn` 필수(400) → 성공 시 `appendRow` 를 EDT 로 넘기고 200 |
| `readBody(in): String?` | 본문 읽기, `MAX_BODY_BYTES`(8KB) 초과 시 `null` |
| `respond(ex, code, msg)` | 응답 전송 + exchange 종료 |
| `startDiscoveryResponder()` | UDP 8000 대기 데몬 스레드. `SN_DISCOVER_V1` 수신 시 `SN_SERVER_V1` 회신. 오류 시 5초 후 소켓 재생성 |
| `buildUi(ip)` | JTable UI 구성. 창닫기 → **확인/취소 대화상자** (확인=종료 로그 후 exit, 취소=유지). 행 `mousePressed` → 즉시 클립보드 복사, Delete 키/버튼 → 행 삭제 |
| `appendRow(dateTime, sn)` | 표에 행 추가, `MAX_ROWS`(1000) 초과 시 오래된 행 제거, 자동 스크롤 |
| `copyToClipboard(text)` | 복사. 다른 프로그램이 클립보드 점유 중이면 100ms 후 1회 재시도 |
| `deleteSelectedRows()` | 선택 행 역순 삭제 |
| `parseTs(ts)` | epoch millis 파싱, 실패 시 현재 시각 |
| `localIp()` | 첫 번째 활성 IPv4 주소 (창 제목 표시용) |

**`ScanFormat`** — 순수 함수 유틸 (테스트: `ScanFormatTest`)
| 메서드 | 역할 |
|---|---|
| `parseFormBody(body): Map` | `k=v&k=v` 폼 본문 파싱 (URL 디코딩 포함) |
| `formatDateTime(epochMillis, zone)` | 표의 날짜/시각 열 형식 `MM-dd HH:mm:ss` |

## 테스트 / 빌드

```bash
# 태블릿 앱 단위 테스트 (조립·중복억제·탐색·전송 재시도)
cd garminpos-app && gradlew.bat :app:testDebugUnitTest

# 태블릿 앱 APK
gradlew.bat :app:assembleDebug   # → app/build/outputs/apk/debug/app-debug.apk

# 수신기 (JDK 필요, lib/ 에 slf4j-api-1.7.36 + logback-classic/core-1.2.13 jar 필요)
cd sn-server
javac -encoding UTF-8 -cp "lib/*" -d build ScanFormat.java SnServer.java ScanFormatTest.java
java -cp "build;lib/*;." ScanFormatTest   # 단위 테스트
java -cp "build;lib/*;." SnServer         # 실행 (logback.xml 은 클래스패스의 . 에서 탐색)
```

## 운영 메모

- 수신기 로그: `SnServer\logs\snserver.log` — 마지막 줄이 `프로세스 종료 (shutdown hook)` 가 아니면 강제 종료/전원 차단이었다는 뜻
- **태블릿 앱 재설치 시 접근성 서비스와 "키보드 입력 읽어주기" 토글이 초기화됨** — 재설치 후 반드시 다시 켜고 실물 스캔으로 확인 (adb 주입 키는 접근성 필터에 도달하지 않음)
