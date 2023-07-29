require('./styles.css')

// internal actual fun Any?.toCustomStringOrNull(): String? =
// if (this == null) null else toString().takeUnless { it == "[object Object]" }

// TODO consider using cycle.js and JSON.stringify

document.onreadystatechange = () => {

  let formattedMessage = format('onreadystatechange %s - %s; %o', 'a string', 42, { a: 1, b: 2 }, 'color: red; font-size: 20px;', 'foo')

  const div = document.createElement('div')
  div.setAttribute('data-msg-type', 'warn')
  const pre = document.createElement('pre')
  pre.textContent = formattedMessage
  div.appendChild(pre)
  document.getElementById('console').appendChild(div)
}

// Formats the message with the given values.
// Supports string substitution and formatting with %s, %d, %i, %f, %o, %O, %c.
// %s logs as Strings.
// %i or %d logs as Integers.
// %f logs as a floating-point value.
// %o logs as an expandable DOM element.
// %O logs as an expandable JavaScript object.
function formatArg(match, arg) {
  switch (match) {
    case '%%':
      return '%'
    case '%i':
    case '%d':
      return Number(arg).toString()
    case '%f':
      return Number(arg).toString()
    case '%o':
    case '%O':
      return JSON.stringify(arg)
    case '%c':
      return ''
    case '%s':
    default:
      let str = String(arg)
      if (typeof arg === 'object' && str === '[object Object]') {
        return JSON.stringify(arg)
      } else {
        return str
      }
  }
}

// %c allows you to style you message with CSS.
function format(message, ...args) {
  let remainingArgs = args
  let messageWithFormattedSubstitutions = message.replace(/%[%idfoOcs]/g, match => {
    let arg = remainingArgs.shift()
    if (arg === undefined) {
      return match
    } else {
      return formatArg(match, arg)
    }
  })
  console.log(remainingArgs)
  remainingArgs.forEach(arg => {
    messageWithFormattedSubstitutions += ' ' + formatArg('%s', arg)
  })
  return messageWithFormattedSubstitutions
}

module.exports = {}
