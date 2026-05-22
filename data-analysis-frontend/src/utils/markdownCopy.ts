let isMarkdownCopyHandlerInstalled = false

const updateCopyTriggerText = (trigger: HTMLElement, text: string) => {
  trigger.dataset.feedback = text
  trigger.setAttribute('aria-label', text)
  trigger.setAttribute('title', text)
}

const setButtonFeedback = (
  trigger: HTMLElement,
  label: string,
  state?: 'success' | 'error'
) => {
  if (!trigger.dataset.defaultFeedback) {
    trigger.dataset.defaultFeedback = trigger.dataset.feedback || '复制'
  }

  updateCopyTriggerText(trigger, label)

  if (state) {
    trigger.dataset.state = state
  } else {
    delete trigger.dataset.state
  }

  const activeTimer = trigger.dataset.resetTimer ? Number(trigger.dataset.resetTimer) : 0
  if (activeTimer) {
    window.clearTimeout(activeTimer)
  }

  const timerId = window.setTimeout(() => {
    updateCopyTriggerText(trigger, trigger.dataset.defaultFeedback || '复制')
    delete trigger.dataset.state
    delete trigger.dataset.resetTimer
  }, 1600)

  trigger.dataset.resetTimer = String(timerId)
}

const fallbackCopyText = (text: string): boolean => {
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', 'true')
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  textarea.style.pointerEvents = 'none'

  document.body.appendChild(textarea)
  textarea.select()

  let success = false
  try {
    success = document.execCommand('copy')
  } catch {
    success = false
  }

  document.body.removeChild(textarea)
  return success
}

const writeClipboardText = async (text: string) => {
  if (navigator.clipboard?.writeText && window.isSecureContext) {
    await navigator.clipboard.writeText(text)
    return
  }

  const success = fallbackCopyText(text)
  if (!success) {
    throw new Error('Clipboard write failed')
  }
}

const findCopyTrigger = (target: EventTarget | null): HTMLElement | null => {
  if (!(target instanceof Element)) {
    return null
  }

  return target.closest('.markdown-code-copy') as HTMLElement | null
}

const copyFromTrigger = async (trigger: HTMLElement) => {
  const codeElement = trigger.closest('.markdown-code-block')?.querySelector('code')
  const codeText = codeElement?.textContent || ''

  if (!codeText.trim()) {
    setButtonFeedback(trigger, '无内容', 'error')
    return
  }

  try {
    await writeClipboardText(codeText)
    setButtonFeedback(trigger, '已复制', 'success')
  } catch (error) {
    console.error('Failed to copy markdown code block', error)
    setButtonFeedback(trigger, '复制失败', 'error')
  }
}

export const installMarkdownCopyHandler = () => {
  if (isMarkdownCopyHandlerInstalled || typeof document === 'undefined') {
    return
  }

  document.addEventListener('click', (event) => {
    const trigger = findCopyTrigger(event.target)
    if (!trigger) {
      return
    }

    event.preventDefault()
    void copyFromTrigger(trigger)
  })

  document.addEventListener('keydown', (event) => {
    if (event.key !== 'Enter' && event.key !== ' ') {
      return
    }

    const trigger = findCopyTrigger(event.target)
    if (!trigger) {
      return
    }

    event.preventDefault()
    void copyFromTrigger(trigger)
  })
  isMarkdownCopyHandlerInstalled = true
}
