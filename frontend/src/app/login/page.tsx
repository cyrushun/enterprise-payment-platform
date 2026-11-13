"use client";
import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { LoaderCircle, LockKeyhole, ShieldCheck } from "lucide-react";

export default function LoginPage() {
  const router = useRouter();
  const [email,setEmail]=useState(""); const [password,setPassword]=useState(""); const [error,setError]=useState(""); const [busy,setBusy]=useState(false);
  async function submit(event:FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(""); setBusy(true);
    try {
      const response=await fetch("/api/session/login",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({email,password})});
      const body=await response.json().catch(()=>({message:"Sign in failed"}));
      if(!response.ok) throw new Error(body.message ?? "Sign in failed");
      const roles:string[]=body.user?.roles ?? [];
      const next=new URLSearchParams(window.location.search).get("next"); const destination=next || (roles.some((r)=>["OPERATIONS","ADMIN"].includes(r))?"/operations/dashboard":"/customer/dashboard");
      router.replace(destination.startsWith("/") && !destination.startsWith("//") ? destination : "/customer/dashboard"); router.refresh();
    } catch(e) { setError(e instanceof Error?e.message:"Unable to sign in"); } finally { setBusy(false); }
  }
  return <main className="grid min-h-screen place-items-center px-5 py-12"><section className="panel w-full max-w-md p-8"><Link href="/" className="flex items-center gap-3"><span className="brand-mark">EP</span><span><b className="text-sm">ENTERPRISE PAYMENT PLATFORM</b><span className="block text-xs text-slate-500">Secure digital banking</span></span></Link><div className="mt-10"><p className="eyebrow">WELCOME BACK</p><h1 className="mt-2 text-3xl font-bold">Sign in to your account</h1><p className="mt-2 text-sm text-slate-500">Use your provisioned Payment Platform credentials.</p></div>
    <form className="mt-8 space-y-5" onSubmit={submit}><label className="block text-sm font-semibold">Email address<input className="form-input mt-2" type="email" autoComplete="username" required value={email} onChange={e=>setEmail(e.target.value)} /></label><label className="block text-sm font-semibold">Password<input className="form-input mt-2" type="password" autoComplete="current-password" required value={password} onChange={e=>setPassword(e.target.value)} /></label>{error&&<p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</p>}<button disabled={busy} className="primary-button w-full">{busy?<LoaderCircle className="animate-spin" size={17}/>:<LockKeyhole size={17}/>}Sign in</button></form>
    <div className="mt-6 flex items-start gap-2 rounded-xl bg-slate-50 p-3 text-xs leading-5 text-slate-500"><ShieldCheck size={16} className="mt-0.5 shrink-0 text-emerald-600"/>Your tokens are held in an encrypted, HttpOnly server session and are not exposed to browser JavaScript.</div><p className="mt-6 text-center text-xs text-slate-500">New customer? Public registration is available through the backend API. Contact your administrator for access.</p>
  </section></main>;
}
