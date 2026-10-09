import { useEffect, useRef, useState } from 'react'
import type { ContentSummary } from '../api/types'
import { apiFetch } from '../api/client'
import { useApi } from '../api/useApi'
import ErrorBanner from '../components/ErrorBanner'
import Spinner from '../components/Spinner'

interface AiAnswer {
  answer: string
  source: string
  relatedContentTitle: string
  engine: string
}

interface Message {
  role: 'user' | 'bot'
  text: string
  meta?: string
}

const QUICK_PROMPTS = ['Explain this model', 'Quiz me', 'Give me a study tip', 'What are the key parts?']

/** Feature-detect Web Speech (STT) and speechSynthesis (TTS) — graceful when absent. */
type SpeechRecognitionCtor = new () => {
  start: () => void
  stop: () => void
  lang: string
  interimResults: boolean
  onresult: ((ev: { results: ArrayLike<{ 0: { transcript: string } }> }) => void) | null
  onerror: (() => void) | null
  onend: (() => void) | null
}
function sttCtor(): SpeechRecognitionCtor | null {
  if (typeof window === 'undefined') return null
  const w = window as unknown as {
    SpeechRecognition?: SpeechRecognitionCtor
    webkitSpeechRecognition?: SpeechRecognitionCtor
  }
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null
}
function sttAvailable(): boolean {
  return sttCtor() !== null
}
function ttsAvailable(): boolean {
  return typeof window !== 'undefined' && 'speechSynthesis' in window
}

/**
 * AI Tutor — grounded chat over the approved lesson material (POST /api/ai/ask),
 * with optional voice input (speech-to-text) and voice output (speech synthesis).
 */
export default function AiTutorPage() {
  const contents = useApi<ContentSummary[]>('/api/ar-content')
  const [contentId, setContentId] = useState<number | null>(null)
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)
  const [listening, setListening] = useState(false)
  const [speaking, setSpeaking] = useState(false)
  const recRef = useRef<{ stop: () => void } | null>(null)
  const bottomRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    if (contentId === null && contents.data && contents.data.length > 0) {
      setContentId(contents.data[0].id)
    }
  }, [contents.data, contentId])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, busy])

  useEffect(() => () => recRef.current?.stop(), [])

  async function ask(text: string) {
    const question = text.trim()
    if (!question || busy || contentId === null) return
    setBusy(true)
    setErr(null)
    setInput('')
    setMessages((m) => [...m, { role: 'user', text: question }])
    try {
      const res = await apiFetch<AiAnswer>('/api/ai/ask', {
        method: 'POST',
        body: JSON.stringify({ contentId, question }),
      })
      setMessages((m) => [
        ...m,
        {
          role: 'bot',
          text: res.answer,
          meta: `${res.engine ?? 'curriculum-grounded'} · ${res.source ?? ''} · ${res.relatedContentTitle ?? ''}`.trim(),
        },
      ])
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  function startListening() {
    const Ctor = sttCtor()
    if (!Ctor) return
    try {
      const rec = new Ctor()
      recRef.current = rec
      rec.lang = 'en-US'
      rec.interimResults = false
      rec.onresult = (ev) => {
        const transcript = ev.results[0]?.[0]?.transcript ?? ''
        if (transcript) setInput((prev) => (prev ? `${prev} ${transcript}` : transcript))
      }
      rec.onerror = () => setListening(false)
      rec.onend = () => setListening(false)
      rec.start()
      setListening(true)
    } catch {
      setListening(false)
    }
  }

  function stopListening() {
    recRef.current?.stop()
    setListening(false)
  }

  function speak(text: string) {
    if (!ttsAvailable()) return
    window.speechSynthesis.cancel()
    const u = new SpeechSynthesisUtterance(text)
    u.rate = 1
    u.onstart = () => setSpeaking(true)
    u.onend = () => setSpeaking(false)
    u.onerror = () => setSpeaking(false)
    window.speechSynthesis.speak(u)
  }

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">AI Tutor</h1>
          <p className="text-sm text-slate-500">Answers are grounded in the approved lesson material only.</p>
        </div>
        <label className="flex flex-col gap-1 text-xs text-slate-400">
          <span>Lesson content</span>
          <select
            value={contentId ?? ''}
            onChange={(e) => setContentId(Number(e.target.value))}
            className="rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-200"
          >
            {(contents.data ?? []).map((c) => (
              <option key={c.id} value={c.id}>
                {c.title}
              </option>
            ))}
          </select>
        </label>
      </div>

      {contents.loading && <Spinner label="Loading content…" />}
      {err && <ErrorBanner error={new Error(err)} title="AI Tutor error" />}

      <div className="space-y-3 rounded-2xl border border-slate-800 bg-slate-900/70 p-5">
        {messages.length === 0 && !busy && (
          <div className="py-6 text-center">
            <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-2xl bg-gradient-to-br from-violet-400/20 to-cyan-500/20 text-2xl">🎓</div>
            <p className="text-sm text-slate-400">Ask anything about the lesson — the tutor only answers from approved material.</p>
            <div className="mt-4 flex flex-wrap justify-center gap-2">
              {QUICK_PROMPTS.map((p) => (
                <button
                  key={p}
                  onClick={() => ask(p)}
                  className="rounded-full border border-slate-700 px-3 py-1.5 text-xs text-slate-300 transition hover:border-cyan-500/50 hover:text-cyan-300"
                >
                  {p}
                </button>
              ))}
            </div>
          </div>
        )}

        {messages.map((m, i) => (
          <div key={i} className={`flex ${m.role === 'user' ? 'justify-end' : 'justify-start'}`}>
            <div
              className={`max-w-[85%] rounded-2xl p-3 text-sm leading-relaxed ${
                m.role === 'user'
                  ? 'bg-gradient-to-r from-cyan-600 to-blue-600 text-white'
                  : 'border border-slate-800 bg-slate-950/60 text-slate-200'
              }`}
            >
              <p className="whitespace-pre-wrap">{m.text}</p>
              {m.meta && <p className="mt-2 text-[10px] text-slate-500">{m.meta}</p>}
              {m.role === 'bot' && ttsAvailable() && (
                <button
                  onClick={() => speak(m.text)}
                  className="mt-2 rounded-lg border border-slate-700 px-2 py-1 text-[10px] text-slate-400 hover:text-slate-200"
                >
                  {speaking ? '🔊 Reading…' : '🔊 Read aloud'}
                </button>
              )}
            </div>
          </div>
        ))}
        {busy && (
          <div className="flex justify-start">
            <div className="rounded-2xl border border-slate-800 bg-slate-950/60 p-3 text-sm text-slate-400">
              <span className="inline-block animate-pulse">Thinking…</span>
            </div>
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      <div className="flex items-center gap-2">
        {sttAvailable() && (
          <button
            onClick={listening ? stopListening : startListening}
            title={listening ? 'Stop listening' : 'Speak your question'}
            className={`rounded-xl border px-4 py-2.5 text-sm transition ${
              listening
                ? 'border-rose-500/60 bg-rose-500/15 text-rose-300 animate-pulse'
                : 'border-slate-700 text-slate-300 hover:border-cyan-500/50 hover:text-cyan-300'
            }`}
          >
            🎙️ {listening ? 'Stop' : 'Mic'}
          </button>
        )}
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && ask(input)}
          placeholder="Ask about the lesson…"
          className="flex-1 rounded-xl border border-slate-700 bg-slate-900 px-4 py-2.5 text-sm text-slate-200 placeholder:text-slate-600 focus:border-cyan-500/60 focus:outline-none"
        />
        <button
          onClick={() => ask(input)}
          disabled={busy || !input.trim() || contentId === null}
          className="rounded-xl bg-gradient-to-r from-cyan-500 to-blue-600 px-5 py-2.5 text-sm font-semibold text-white transition hover:from-cyan-400 hover:to-blue-500 disabled:opacity-40"
        >
          Ask
        </button>
      </div>
    </div>
  )
}