# Triển khai lên VPS (phương án A)

Một VPS chạy toàn bộ hệ thống bằng Docker Compose ([`docker-compose.prod.yml`](../docker-compose.prod.yml)):

```
Internet ──443/80──▶ caddy (HTTPS tự động, Let's Encrypt)
                       │ reverse proxy :8080
                       ▼
                    backend (Spring Boot) ──▶ postgres (dữ liệu)
                       │  :8081 actuator      └▶ redis (cache dashboard / tài sản ròng)
                       ▼
                 prometheus (tuỳ chọn, chỉ 127.0.0.1:9090)
backup ──▶ pg_dump + ảnh hoá đơn hằng đêm ──▶ /backups trên VPS + object storage (S3/R2/B2…)
```

- Chỉ Caddy mở cổng ra ngoài (80, 443). Postgres, Redis, backend không công khai cổng nào.
- Actuator (health, metrics Prometheus) chỉ ở cổng nội bộ `8081`. Caddy không chuyển tiếp cổng này, nên từ Internet
  gọi `/actuator/*` sẽ nhận `404`. Swagger cũng tắt mặc định (`API_DOCS_ENABLED=false`).

## 1. Chuẩn bị

| Hạng mục | Khuyến nghị |
|---|---|
| VPS | 2 vCPU, 2–4 GB RAM, 40 GB SSD, Ubuntu 24.04 LTS. Ví dụ Hetzner CX22, DigitalOcean 2 GB, Vultr… (khoảng 5–12 USD/tháng) |
| Tên miền | Bản ghi `A` (và `AAAA` nếu có IPv6) `api.example.com` → IP của VPS, tạo **trước** khi chạy Caddy |
| Object storage | Bucket riêng cho backup: Cloudflare R2 (10 GB miễn phí), Backblaze B2, AWS S3… và một access key chỉ có quyền trên bucket đó |
| Email | SMTP để gửi mã xác thực và mã đặt lại mật khẩu, ví dụ Brevo (300 email/ngày miễn phí), Amazon SES, Mailgun |

Cài Docker và tường lửa trên VPS:

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER        # đăng xuất rồi đăng nhập lại
sudo ufw allow OpenSSH && sudo ufw allow 80/tcp && sudo ufw allow 443/tcp && sudo ufw allow 443/udp
sudo ufw enable
```

Docker tự thêm luật iptables cho các cổng nó publish. Vì vậy file compose chỉ publish cổng của Caddy, còn Prometheus
chỉ publish ở `127.0.0.1`.

## 2. Cấu hình

```bash
git clone https://github.com/rockman1510/financemanage_backend.git financemanage && cd financemanage
cp .env.prod.example .env
chmod 600 .env
nano .env
```

- Điền `DOMAIN` và `ACME_EMAIL`.
- Tạo mỗi bí mật bằng `openssl rand -base64 48`: `DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`, `ENCRYPTION_KEY`,
  `BACKUP_PASSPHRASE`.
- **`ENCRYPTION_KEY` và `BACKUP_PASSPHRASE` phải được cất giữ ở nơi khác ngoài VPS**, ví dụ trong trình quản lý mật khẩu:
  - mất `ENCRYPTION_KEY` thì người dùng không dùng được 2FA đã bật;
  - mất `BACKUP_PASSPHRASE` thì không giải mã được bản backup.
- SMTP (`SMTP_HOST`…): không có thì email chỉ ghi vào log, người dùng không nhận được mã.
- `GOOGLE_CLIENT_IDS`: client id OAuth của app mobile, để bật đăng nhập Google.
- Backup ra ngoài:
  - Điền `S3_BUCKET`, `S3_ACCESS_KEY_ID`, `S3_SECRET_ACCESS_KEY`.
  - Với R2, B2, Spaces thì điền thêm `S3_ENDPOINT`, ví dụ R2: `https://<account-id>.r2.cloudflarestorage.com`.
  - Đặt **lifecycle rule** trên bucket để tự xoá bản cũ, ví dụ sau 30 ngày.

## 3. Lấy image và khởi động

Image được workflow [`release.yml`](../.github/workflows/release.yml) dựng và đẩy lên GitHub Container Registry khi
push lên `main` hoặc tạo tag `v*`. Cũng có thể chạy tay: Actions → Release images → Run workflow. Repo riêng tư thì
đăng nhập trước bằng một Personal Access Token có quyền `read:packages`:

```bash
echo <token> | docker login ghcr.io -u <github-user> --password-stdin
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps        # backend phải "healthy" (khởi động ~1 phút)
curl https://api.example.com/api/v1/me             # 401 = chạy đúng (chưa đăng nhập)
```

Không dùng registry thì dựng ngay trên VPS bằng `docker compose -f docker-compose.prod.yml build`. Cách này cần ≥ 2 GB
RAM và vài phút vì Gradle biên dịch toàn bộ.

Flyway tự chạy migration khi backend khởi động. Event chưa xử lý xong trước lần dừng trước được gửi lại khi khởi động
lại (`republish-outstanding-events-on-restart`).

## 4. Cập nhật phiên bản

```bash
git pull                                            # lấy compose/Caddyfile mới (nếu có)
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d     # chỉ tạo lại container có image mới
docker image prune -f
```

- Backend dừng êm (graceful shutdown): request đang chạy được hoàn tất, sau đó container mới khởi động. Có khoảng
  30–60 giây gián đoạn. Đây là giới hạn của phương án A (một instance).
- Muốn về bản cũ: đặt `BACKEND_IMAGE=ghcr.io/rockman1510/financemanage-backend:sha-<commit>` trong `.env` rồi chạy lại
  `up -d`. Lưu ý: migration đã chạy thì không tự lùi, nên **backup trước mỗi lần cập nhật lớn** (mục 5).

## 5. Backup và khôi phục

Dịch vụ `backup` chạy mỗi ngày lúc `BACKUP_TIME` (mặc định 02:30 giờ Việt Nam):

1. `pg_dump` toàn bộ CSDL thành `db-<thời gian>.dump`.
2. Nén ảnh hoá đơn thành `attachments-<thời gian>.tar.gz`.
3. Mã hoá AES-256 nếu có `BACKUP_PASSPHRASE` (thêm đuôi `.enc`).
4. Giữ `BACKUP_LOCAL_RETENTION_DAYS` ngày trong volume `backups` và chép lên bucket nếu có `S3_BUCKET`.

```bash
docker compose -f docker-compose.prod.yml exec backup backup.sh once      # backup ngay
docker compose -f docker-compose.prod.yml exec backup ls -lh /backups     # xem các bản đã có
docker compose -f docker-compose.prod.yml logs backup                     # kết quả các lần chạy
```

Redis chỉ là cache (không lưu xuống đĩa) nên không cần backup.

**Khôi phục** (thay toàn bộ dữ liệu hiện tại bằng bản backup):

```bash
docker compose -f docker-compose.prod.yml stop backend
# từ bản trên VPS:
docker compose -f docker-compose.prod.yml exec backup restore.sh db-20261031-023000.dump.enc
# hoặc tải từ bucket:
docker compose -f docker-compose.prod.yml exec backup restore.sh s3:db-20261031-023000.dump.enc s3:attachments-20261031-023000.tar.gz.enc
# ảnh hoá đơn (volume chỉ đọc trong dịch vụ backup, restore.sh để sẵn file đã giải mã; giải nén bằng container tạm):
docker run --rm -v financemanage_attachments:/attachments -v financemanage_backups:/backups alpine:3.22 \
  sh -c 'find /attachments -mindepth 1 -delete && tar -xzf /backups/restore-attachments.tar.gz -C /attachments'
docker compose -f docker-compose.prod.yml start backend
```

Nên **thử khôi phục định kỳ** (ví dụ mỗi quý) trên một máy khác. Một bản backup chưa từng khôi phục thử thì chưa
chắc dùng được.

**Chuyển sang VPS mới**:

1. Cài như mục 1–2 với cùng `.env`, nhất là `ENCRYPTION_KEY` và `BACKUP_PASSPHRASE`.
2. `up -d postgres backup`.
3. `restore.sh s3:...`.
4. `up -d`.
5. Đổi DNS sang IP mới.

## 6. Giám sát

- **Health**: `docker compose -f docker-compose.prod.yml ps` (cột STATUS). Caddy cũng kiểm tra
  `/actuator/health/readiness` mỗi 15 giây. Readiness gồm cả kết nối DB và Redis.
- **Uptime từ bên ngoài**: theo dõi `https://<DOMAIN>/api/v1/me` bằng UptimeRobot hoặc Better Stack (gói miễn phí).
  Kỳ vọng HTTP `401`.
- **Prometheus**: chạy `docker compose -f docker-compose.prod.yml --profile monitoring up -d`, mở bằng
  `ssh -L 9090:localhost:9090 user@vps` rồi vào `http://localhost:9090`. Các metric hữu ích:
  - `http_server_requests_seconds_*`: độ trễ, số request theo endpoint và mã lỗi;
  - `jvm_memory_used_bytes`;
  - `hikaricp_connections_active`;
  - `spring_modulith_events_*` / bảng `event_publication`: event chưa xử lý xong.
- **Log**: `docker compose -f docker-compose.prod.yml logs -f backend caddy`. Log được xoay vòng (20 MB × 5 mỗi
  container). Truy vấn chậm hơn 500 ms được Postgres ghi vào log của nó.
- **Event tồn đọng**: hàng trong `event_publication` có `completion_date IS NULL` lâu hơn vài phút là listener lỗi
  (xem log backend). Event được gửi lại khi khởi động lại.

## 7. Bảo mật – checklist

- [ ] `.env` có quyền `600`, không commit, các bí mật sinh ngẫu nhiên và khác nhau.
- [ ] SSH chỉ cho phép đăng nhập bằng khoá (`PasswordAuthentication no`), cân nhắc `fail2ban`.
- [ ] `ufw` chỉ mở 22, 80, 443.
- [ ] Bật cập nhật bảo mật tự động: `sudo apt install unattended-upgrades`.
- [ ] Backup ra ngoài VPS đã mã hoá, bucket có lifecycle rule, đã thử khôi phục.
- [ ] Access key của bucket chỉ có quyền trên đúng bucket đó.
- [ ] `CORS_ALLOWED_ORIGINS` để trống nếu chỉ có app mobile.
- [ ] `API_DOCS_ENABLED=false`.

## 8. Khi nào cần nâng cấp

Theo [ARCHITECTURE_PLAN.md](ARCHITECTURE_PLAN.md), nâng cấp khi một trong các dấu hiệu sau kéo dài:

| Dấu hiệu | Bước tiếp theo |
|---|---|
| CPU > 70% hoặc RAM gần đầy | Tăng cấu hình VPS (dọc) |
| Cần không gián đoạn khi cập nhật | Chuyển Postgres sang dịch vụ quản lý, chạy ≥ 2 backend sau load balancer. Cache đã dùng chung qua Redis; job đã có ShedLock nên chạy nhiều instance an toàn |
| Truy vấn báo cáo chậm | Read model `reporting.entry` đã tách riêng, có thể chuyển sang replica đọc |
