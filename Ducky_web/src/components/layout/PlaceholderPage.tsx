import { AppShell } from "./AppShell";

interface PlaceholderPageProps {
  title: string;
  description: string;
}

export function PlaceholderPage({ title, description }: PlaceholderPageProps) {
  return (
    <AppShell>
      <section className="mx-auto flex w-full max-w-3xl flex-1 flex-col justify-center px-5 py-10 sm:px-8 lg:px-10">
        <p className="mb-3 text-sm font-semibold text-[#B88700]">Ducky</p>
        <h1 className="text-3xl font-bold tracking-tight text-gray-950 sm:text-4xl">
          {title}
        </h1>
        <p className="mt-4 max-w-xl text-base leading-relaxed text-gray-600 break-keep sm:text-lg">
          {description}
        </p>
      </section>
    </AppShell>
  );
}
