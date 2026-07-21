export function interfaceIncludes(source, interfaceName, fragment) {
  const declaration = `export interface ${interfaceName}`
  const declarationStart = source.indexOf(declaration)
  if (declarationStart < 0) {
    return false
  }

  const bodyStart = source.indexOf('{', declarationStart + declaration.length)
  if (bodyStart < 0) {
    return false
  }

  let depth = 0
  for (let index = bodyStart; index < source.length; index += 1) {
    if (source[index] === '{') {
      depth += 1
    } else if (source[index] === '}') {
      depth -= 1
      if (depth === 0) {
        return source.slice(bodyStart + 1, index).includes(fragment)
      }
    }
  }

  return false
}
