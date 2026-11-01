#!/usr/bin/env bash
# Bundles factory_engine/ into AES-256 encrypted vault.enc so your public repo code stays private
set -e
if [ -z "$MASTER_DECRYPTION_KEY" ]; then
  echo "Usage: MASTER_DECRYPTION_KEY='your-secret-key' ./encrypt_repo_payload.sh"
  exit 1
fi
tar -czf engine.tar.gz factory_engine/
openssl enc -aes-256-cbc -pbkdf2 -salt -in engine.tar.gz -out vault.enc -pass env:MASTER_DECRYPTION_KEY
rm -f engine.tar.gz
echo "Created vault.enc! You can now commit vault.enc to your public GitHub repository."
