# OAuth 설정 안내

소스에서 직접 빌드한 앱으로 Gmail, Outlook, Microsoft To Do를 사용하려면 빌드에 사용한
패키지명과 인증서 지문에 맞춰 OAuth 애플리케이션을 등록해야 합니다. 클라이언트 ID는
공개 식별자이며 클라이언트 비밀키를 Android 앱에 넣지 않습니다.

## Microsoft

1. Microsoft Entra 관리 센터에서 네이티브/모바일 공개 클라이언트 앱을 등록합니다.
2. 개인 Microsoft 계정을 허용하고 Android 플랫폼을 추가합니다.
3. 패키지명 `com.example.galaxycalendarprobe`와 빌드 인증서의 서명 해시를 등록합니다.
4. 위임 권한 `Mail.ReadBasic`, `Tasks.Read`를 추가합니다.
5. `app/src/main/res/raw/auth_config_single_account.json`의 `client_id`와 `redirect_uri`를
   자신의 등록값으로 변경합니다.

앱은 단일 Microsoft 계정을 Outlook과 To Do가 함께 사용합니다. 로그아웃하면 두 기능의
연결과 예약 상태가 함께 정리됩니다.

## Google

1. Google Cloud 프로젝트에서 Gmail API를 사용 설정합니다.
2. OAuth 동의 화면을 구성하고 Android OAuth 클라이언트를 만듭니다.
3. 패키지명과 빌드 인증서 SHA-1을 등록합니다.
4. 앱이 요청하는 범위는 `https://www.googleapis.com/auth/gmail.metadata`입니다.

개발 중에는 저장소에 포함되지 않는 로컬 디버그 인증서를 사용할 수 있습니다. 다른
인증서로 빌드하면 Google과 Microsoft 콘솔에도 해당 인증서 지문을 별도로 등록해야 합니다.
