import { afterEach, describe, expect, it } from "vitest";
import { openSession, sealSession, type AppSession } from "../src/lib/session/crypto";
const original = process.env.SESSION_SECRET;
const session: AppSession = { accessToken:"access-token", refreshToken:"refresh-token", accessTokenExpiresAt:Date.now()+60000, user:{userId:"user-1",customerId:"customer-1",email:"customer@example.test",roles:["CUSTOMER"]} };
afterEach(()=>{if(original===undefined)delete process.env.SESSION_SECRET;else process.env.SESSION_SECRET=original;});
describe("encrypted session cookie",()=>{
 it("round-trips session data without storing it as readable JSON",()=>{process.env.SESSION_SECRET="test-session-secret-with-at-least-32-characters";const value=sealSession(session);expect(value).not.toContain("access-token");expect(openSession(value)).toEqual(session);});
 it("rejects a tampered cookie",()=>{process.env.SESSION_SECRET="test-session-secret-with-at-least-32-characters";const value=sealSession(session);const [iv,tag,ciphertext]=value.split(".");const changed=`${iv[0]==="A"?"B":"A"}${iv.slice(1)}.${tag}.${ciphertext}`;expect(openSession(changed)).toBeNull();});
});
