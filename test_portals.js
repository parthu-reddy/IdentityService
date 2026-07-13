import fs from 'fs';

const BASE_URL = 'http://localhost:8080/api/v1/internal/auth';
const TEST_PHONE = '+919999999999';

async function testLogin(portal, count) {
  console.log(`\nTesting logins for portal: ${portal}`);
  
  for (let i = 1; i <= count; i++) {
    console.log(`\n--- Login Attempt ${i} for ${portal} ---`);
    // 1. Request OTP
    let res = await fetch(`${BASE_URL}/login/phone`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Calling-Service': portal
      },
      body: JSON.stringify({ phoneNumber: TEST_PHONE })
    });
    
    let text = await res.text();
    if (res.status !== 200) {
      console.log(`Failed to request OTP: ${res.status} ${text}`);
      continue;
    }
    
    // Parse OTP from response or check db if needed. Wait, in dev mode, OTP is returned in the message or something?
    // Oh, the OTP is in the redis cache. But there's a dev route.
    console.log(`OTP requested. Response: ${text}`);
    
    // In dev profile, OTP is stored in redis. We can fetch it.
    // Wait, let's just fetch it using redis-cli.
    
  }
}

testLogin('CUSTOMER', 1);
