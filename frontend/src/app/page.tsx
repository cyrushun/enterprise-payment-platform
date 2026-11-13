import Link from "next/link";
import { ArrowUpRight, Building2, ShieldCheck, WalletCards, LogIn } from "lucide-react";

export default function HomePage() {
  return <main className="min-h-screen px-6 py-10 md:px-12">
    <div className="mx-auto max-w-6xl">
      <div className="flex items-center justify-between gap-3"><div className="flex items-center gap-3"><div className="brand-mark">EP</div><div><p className="text-sm font-bold tracking-tight">ENTERPRISE PAYMENT PLATFORM</p><p className="text-xs text-slate-500">Digital banking experience</p></div></div><Link href="/login" className="secondary-button"><LogIn size={16}/>Sign in</Link></div>
      <section className="mt-20 max-w-3xl"><p className="eyebrow">PHASE 6 · CUSTOMER & OPERATIONS</p><h1 className="mt-4 text-4xl font-bold tracking-tight text-slate-950 md:text-6xl">Payments, made clear.</h1><p className="mt-5 max-w-2xl text-lg leading-8 text-slate-500">A unified experience for customers and payment operations teams, backed by the Enterprise Payment Platform API.</p></section>
      <div className="mt-12 grid gap-5 md:grid-cols-2">
        <PortalCard href="/customer/dashboard" icon={<WalletCards size={22}/>} title="Customer portal" description="Accounts, balances, transfers and transaction activity." label="CUSTOMER EXPERIENCE" />
        <PortalCard href="/operations/dashboard" icon={<Building2 size={22}/>} title="Operations portal" description="Transfer oversight, reconciliation, audit and event operations." label="OPERATIONS WORKSPACE" />
      </div>
      <div className="mt-8 flex items-center gap-2 text-xs text-slate-500"><ShieldCheck size={15}/> Backend authorization remains authoritative. Sign in to access your role-authorized workspace.</div>
      <p className="mt-16 text-xs text-slate-400">Educational simulation only · Not intended to process real customer funds</p>
    </div>
  </main>;
}
function PortalCard({href,icon,title,description,label}:{href:string;icon:React.ReactNode;title:string;description:string;label:string}) { return <Link href={href} className="panel group p-7 transition hover:-translate-y-1 hover:shadow-soft"><div className="flex items-center justify-between"><div className="grid h-12 w-12 place-items-center rounded-2xl bg-indigo-50 text-indigo-700">{icon}</div><ArrowUpRight className="text-slate-300 transition group-hover:text-indigo-600"/></div><p className="eyebrow mt-8">{label}</p><h2 className="mt-2 text-2xl font-bold">{title}</h2><p className="mt-2 max-w-sm text-sm leading-6 text-slate-500">{description}</p><p className="mt-7 text-sm font-semibold text-indigo-700">Open workspace <span aria-hidden="true">→</span></p></Link>; }
