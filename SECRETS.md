Secrets
=======

This document contains information about the secrets that this service uses.
The shell commands were writting on MacOS so you might need to alter the `base64` calls, the flags are different on Linux.

You may assume that the paragraphs with **How to generate this secret?** always produce base64 encoded output that is ready to be pasted in k8s.

tokens-encryption-shared-secret / secret
---

**Type**

64 base64 encoded random bytes.

**Spring property**

`service.tokens.encryption-shared-secret`

**Getting the secret from k8s**

`$ kubectl get secret tokens-encryption-shared-secret -o json | jq -r '.data.secret' | base64 -D | base64 -D | wc -c`

**How to check if the currently configured secret is correct?**

The secret should be 64 bytes.

**How to generate this secret?**

`$ kubectl create secret generic tokens-encryption-shared-secret --from-literal=secret=$(openssl rand 64 | base64 | base64)`

**Notes**

This secret is also used by `backend` / `tokens`.

tokens-signature-jwks / secret
---

**Type**

A list of RSA public keys in jwks format, see also: RSA JSON Web Key (Jwk) -- https://tools.ietf.org/html/rfc7517

**Spring property**

`service.tokens.signature-jwks`

**Getting the secret from k8s**

`$ secret=$(kubectl get secret tokens-signature-jwks -o json | jq -r '.data.secret' | base64 -D | jq)`

**How to check if the currently configured secret is correct?**

At least one of the keys in the list should be the public part of the private key configured in the `backend` / `tokens` project.


**How to generate this secret?**

Follow the instructions in `SECRETS.md` of the `backend` / `tokens` project to generate the file `pub.json`.

Extract the keyset from k8s by following the instructions in `**Getting the secret from k8s**` and write it to `keyset.json`

Run this to add the public key (updating the keyset).
`$ step crypto jwk keyset add keyset.json < pub.json`

Run this to get a base64 string that is ready to store in k8s:
`$ jq --compact-output < keyset.json | base64`
