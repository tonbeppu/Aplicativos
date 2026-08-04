# Wi Ton

App Android que mede a intensidade do sinal de Wi-Fi em realidade aumentada,
monta um mapa 2D do ambiente com heatmap e exporta o resultado em PDF.

- **Pacote:** `com.movedados.witon`
- **Backend:** Supabase (`ylysgozabadglznclmpn`)
- **AR:** ARCore + SceneView (sprint 3)

---

## Rodando pela primeira vez

```bash
cp local.properties.example local.properties
```

Preencha `local.properties`:

```properties
sdk.dir=/caminho/para/o/Android/Sdk
SUPABASE_URL=https://ylysgozabadglznclmpn.supabase.co
SUPABASE_ANON_KEY=<anon key do painel: Settings > API>
```

Depois:

```bash
./gradlew assembleDebug
```

O Gradle Wrapper 8.9 ja acompanha o projeto (`gradlew`, `gradlew.bat`,
`gradle/wrapper/`). Ele **precisa** estar versionado: e o que o CI usa.
No Linux/macOS, apos o primeiro clone: `chmod +x gradlew`.

> A **anon key** e publica por design — quem protege os dados e o RLS.
> A **service_role key** nunca pode entrar no app.

---

## Fluxo de acesso

```
cadastro (email/senha) -> profile status = 'pending'
                       -> login funciona, mas o banco recusa gravar leituras
                       -> admin libera -> status = 'approved' -> app abre
```

O admin e semeado por email: uma conta criada com `tonbeppu@gmail.com`
recebe `role = 'admin'` e `status = 'approved'` automaticamente pelo trigger
`handle_new_user`. Para criar: Dashboard > Authentication > Users > Add user,
marcando **Auto Confirm User**.

A navegacao e dirigida pelo `Gate` (ver `ui/auth/AuthViewModel.kt`), nao por
cliques: nenhuma tela protegida chega a existir na pilha se o acesso nao
estiver liberado.

---

## Estrutura

```
app/src/main/java/com/movedados/witon/
├── core/          ServiceLocator (DI manual)
├── data/
│   ├── local/     Room — a leitura grava aqui primeiro (offline-first)
│   ├── remote/    cliente Supabase + DTOs
│   └── repository/ AuthRepository, AdminRepository
├── wifi/          RssiSampler, WifiStateMonitor, RssiScale
└── ui/            theme, components, navigation, auth, admin, home
supabase/migrations/  SQL versionado (espelha o que esta aplicado)
```

---

## Decisoes que valem conhecer antes de mexer

**Por que polling e nao `startScan()`.** O Android limita `startScan()` a 4
chamadas a cada 2 minutos — inviabiliza tempo real. Esse limite vale para
varredura de redes; a leitura do RSSI da conexao atual nao e throttled. Por isso
o `RssiSampler` usa `getConnectionInfo()` a cada 400 ms com media exponencial.
O metodo esta deprecado desde a API 31, mas continua sendo o caminho mais
estavel para amostragem em intervalo fixo.

**Por que a escala de cor nao e linear.** Uma rampa de -100 a 0 dBm jogaria
quase tudo no vermelho. Os cortes em `RssiScale` seguem as referencias de
projeto de rede, com -67 dBm como limite util para voz e video.

**Por que Room antes do Supabase.** O Wi-Fi e o objeto do teste: a captura nao
pode depender da rede que ela mesma esta medindo. A sincronizacao acontece em
lote quando o usuario encerra a leitura.

**Permissao de localizacao.** Sem `ACCESS_FINE_LOCATION`, o Android devolve
`<unknown ssid>` e mascara o BSSID. Nao e opcional para este app.

---

## Roadmap

| Sprint | Entrega | Status |
|---|---|---|
| 1 | Auth por email/senha + aprovacao do admin | feito |
| 2 | `RssiSampler` com HUD numerico ao vivo | feito (tela Home) |
| 3 | ARSceneView + baloes coloridos + persistencia | a fazer |
| 4 | IDW + heatmap 2D | a fazer |
| 5 | Contorno da planta + paredes | a fazer |
| 6 | PDF + upload + historico | a fazer |

## CI no GitHub

Os dois workflows em `.github/workflows/` precisam ser copiados para a **raiz do
repositorio** se o projeto estiver em subpasta — o GitHub so le workflows de
`<raiz>/.github/workflows/`. Ajuste `PROJECT_DIR` no topo de cada um.

Secrets necessarios (Settings > Secrets and variables > Actions):

| Secret | Usado em |
|---|---|
| `SUPABASE_URL` | CI e Release |
| `SUPABASE_ANON_KEY` | CI e Release |
| `WITON_KEYSTORE_BASE64` | Release |
| `WITON_KEYSTORE_PASSWORD` | Release |
| `WITON_KEY_ALIAS` | Release |
| `WITON_KEY_PASSWORD` | Release |

Gerar o keystore e o base64:

```bash
keytool -genkeypair -v -keystore witon-release.jks -keyalg RSA -keysize 2048 \
        -validity 10000 -alias witon
base64 -w0 witon-release.jks > witon-release.jks.b64   # macOS: base64 -i ...
```

Guarde o `.jks` fora do git e em lugar seguro: perder esse arquivo significa
nao conseguir mais atualizar o app publicado na Play Store.

---

## Antes de publicar

- [ ] Gerar keystore de release e configurar `signingConfig`
- [ ] Plugar SMTP proprio (o SMTP nativo do Supabase e limitado a poucos emails/hora)
- [ ] Incluir o aviso obrigatorio do ARCore ("This application runs on Google
      Play Services for AR, provided by Google LLC...")
- [ ] Politica de privacidade descrevendo a coleta de dados de rede
