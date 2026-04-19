# Shipping `krill-mcp` via the Debian repo workflow

The private `krill` repo owns `.github/workflows/Deploy Debian Repo.yml` — the
workflow that builds and signs every `.deb` in `s3://deb.krill.zone`. To ship
`krill-mcp`, add the steps below to that workflow, mirroring the existing
`krill-pi4j` block.

## Where to drop it

Insert the `Build krill-mcp` block **immediately after** the existing
`Build krill-pi4j deb package` step. It can run under either JDK 21 or 25 —
the Gradle toolchain will provision the right JVM via foojay.

Insert the `cp` line inside the **"📦 Copying new packages to pool..."**
section of the `Update Debian repository on S3` step.

## Workflow snippet

```yaml
      # ── krill-mcp server ─────────────────────────────────────────────────────
      # Remote Model Context Protocol server that lets Claude Desktop / Code
      # talk to a Krill swarm. Pure JVM, Architecture: all.

      - name: Build krill-mcp fat JAR
        timeout-minutes: 10
        working-directory: krill-oss/krill-mcp
        run: |
          echo "🦐 Building krill-mcp fat JAR..."
          ./gradlew :krill-mcp-service:shadowJar --no-configuration-cache
          JAR="./krill-mcp-service/build/libs/krill-mcp-all.jar"
          if [ ! -f "$JAR" ]; then
            echo "❌ ERROR: fat JAR not found at $JAR"
            ls -lh ./krill-mcp-service/build/libs/ || true
            exit 1
          fi
          echo "✅ krill-mcp-all.jar built ($(du -h $JAR | cut -f1))"

      - name: Build krill-mcp deb package
        working-directory: krill-oss/krill-mcp
        run: |
          mkdir -p krill-mcp-service/package/usr/local/bin
          cp krill-mcp-service/build/libs/krill-mcp-all.jar \
             krill-mcp-service/package/usr/local/bin/krill-mcp.jar

          chmod 755 krill-mcp-service/package/DEBIAN/preinst \
                     krill-mcp-service/package/DEBIAN/postinst \
                     krill-mcp-service/package/DEBIAN/prerm \
                     krill-mcp-service/package/DEBIAN/postrm
          chmod 755 krill-mcp-service/package/usr/local/bin/krill-mcp.jar
          chmod 755 krill-mcp-service/package/usr/local/bin/krill-mcp-token

          mkdir -p krill-mcp-service/build
          dpkg-deb --build krill-mcp-service/package \
                           krill-mcp-service/build/krill-mcp.deb
          dpkg-deb --info  krill-mcp-service/build/krill-mcp.deb
          echo "📦 krill-mcp.deb built"
```

## Line to add inside the S3 pool copy step

Find this block in the existing `Update Debian repository on S3` step:

```bash
echo "📦 Copying new packages to pool..."
cp ./server/build/krill.deb "$POOL_DIR/"
cp ./composeApp/build/krill-desktop.deb "$POOL_DIR/"
cp ./krill-oss/pi4j-ktx-service/krill-pi4j-service/build/krill-pi4j.deb "$POOL_DIR/"
```

Add one more line:

```bash
cp ./krill-oss/krill-mcp/krill-mcp-service/build/krill-mcp.deb "$POOL_DIR/"
```

That's it — the existing `dpkg-scanpackages` / `Release` / sign / S3 sync /
CloudFront invalidation steps pick up the new `.deb` with no further changes.

## Install path for end users

Once published:

```bash
sudo apt update
sudo apt install krill-mcp
# postinst prompts for the 4-digit cluster PIN
# (or silently shares /etc/krill/credentials/pin_derived_key if krill is already installed)
```

Then add a Custom Connector in Claude with the URL + Bearer header that
`postinst` printed (or re-run `sudo krill-mcp-token` at any time).
