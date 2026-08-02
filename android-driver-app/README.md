# MoveTV Driver - Aplicativo Android

Aplicativo Android nativo (Kotlin) para motoristas do sistema MoveTV. Espelha a tela de motorista do painel web, coletando dados de GPS e enviando para o Supabase.

## Funcionalidades

- **Login** com email e senha (Supabase Auth)
- **Aba Home**: lista de campanhas com tipos de mídia e benefícios
- **Aba Monitoramento**: cards de logins, tempo online, campanhas ativas/concluídas, pontos GPS e km rodados (últimos 7 dias)
- **Aba Perfil**: dados pessoais, veículo e informações de pagamento (PIX)
- **GPS em tempo real**: serviço em primeiro plano que envia localização a cada 30 segundos para o Supabase

## Requisitos

- Android Studio Hedgehog (2023.1.1) ou superior
- JDK 17
- Android SDK 34 (compileSdk)
- Min SDK 24 (Android 7.0)

## Como Buildar

### 1. Abrir o projeto

1. Abra o Android Studio
2. Selecione **File > Open** e escolha a pasta `android-driver-app`
3. Aguarde a sincronização do Gradle

### 2. Gerar o APK (debug ou release)

#### APK Debug (para testes rápidos)
```bash
./gradlew assembleDebug
```
O arquivo será gerado em `app/build/outputs/apk/debug/app-debug.apk`

#### APK Release (para distribuição)
```bash
./gradlew assembleRelease
```
O arquivo será gerado em `app/build/outputs/apk/release/app-release.apk`

### 3. Gerar o AAB (para publicação na Google Play)

```bash
./gradlew bundleRelease
```
O arquivo será gerado em `app/build/outputs/bundle/release/app-release.aab`

## Assinatura para Produção

Para publicar na Google Play, você precisa de uma chave de assinatura.

### Criar uma keystore

```bash
keytool -genkey -v -keystore movetv-driver.jks -keyalg RSA -keysize 2048 -validity 10000 -alias movetv
```

### Configurar a assinatura no projeto

1. Copie o arquivo `.jks` para a pasta `app/`
2. Crie o arquivo `app/keystore.properties` com:
```properties
storePassword=SUA_SENHA
keyPassword=SUA_SENHA
keyAlias=movetv
storeFile=../movetv-driver.jks
```
3. No `app/build.gradle`, adicione no bloco `android {}`:
```gradle
def keystoreProperties = new Properties()
keystoreProperties.load(new FileInputStream(rootProject.file("app/keystore.properties")))
android {
    signingConfigs {
        release {
            storeFile file(keystoreProperties['storeFile'])
            storePassword keystoreProperties['storePassword']
            keyAlias keystoreProperties['keyAlias']
            keyPassword keystoreProperties['keyPassword']
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
        }
    }
}
```

## Instalação em Dispositivo

```bash
# Com o dispositivo conectado via USB com depuração ativada
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Permissões do App

- **Internet**: comunicação com Supabase
- **Localização (precisa e aproximada)**: rastreamento GPS
- **Serviço em primeiro plano**: manter GPS ativo em background
- **Notificações** (Android 13+): notificação do serviço de localização

## Configuração do Supabase

O app já vem configurado com as credenciais do projeto:
- URL: `https://nhnnpqbxobfsqhdutqbh.supabase.co`
- Tabelas usadas: `profiles`, `campaign_devices`, `campaigns`, `campaign_types`, `audit_logs`, `driver_gps_logs`

## Estrutura do Projeto

```
app/src/main/java/com/movedados/movetv/driver/
├── MoveTVDriverApp.kt          # Application + canal de notificação
├── ui/
│   ├── MainActivity.kt         # Activity principal com bottom navigation
│   ├── login/LoginActivity.kt   # Tela de login
│   ├── home/HomeFragment.kt     # Aba Home - campanhas
│   ├── monitoring/MonitoringFragment.kt  # Aba Monitoramento
│   └── profile/ProfileFragment.kt        # Aba Perfil
├── models/Models.kt             # Data classes
├── network/SupabaseClient.kt   # Cliente HTTP para Supabase
├── services/LocationService.kt  # Serviço GPS em primeiro plano
└── utils/PreferenceManager.kt   # Gerenciador de SharedPreferences
```

## Publicação na Google Play Store

1. Gere o AAB: `./gradlew bundleRelease`
2. Acesse o [Google Play Console](https://play.google.com/console)
3. Crie um novo aplicativo com o package `com.movedados.movetv.driver`
4. Faça upload do arquivo `.aab` na seção "Produção"
5. Preencha as informações da loja (descrição, screenshots, ícone)
6. Envie para revisão
