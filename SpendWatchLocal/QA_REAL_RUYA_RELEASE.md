# Real Ruya notification regression check

v4.1.0 verification candidate.

Purchase example (account/card endings replaced with dummy digits):

`Dear Customer, Debit Card Purchase of AED 39.95 from account ending with 1234 was done by Card ending with 5678 from TIM HORTONS on 08/10/2026, your available balance is AED 326.77`

Expected: one card purchase, AED 39.95, TIM HORTONS, Food & Dining; no second purchase for the balance AED 326.77. Repeating the alert must not produce a duplicate. Transaction confirmation must appear after successful save.

The emulator QA workflow and parser unit tests are authoritative for this release candidate; physical Ruya/SMS-delivery behavior still requires on-device checks.
