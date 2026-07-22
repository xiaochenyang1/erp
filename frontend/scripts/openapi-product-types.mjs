import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import openapiTS, { astToString, COMMENT_HEADER } from 'openapi-typescript'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const artifactPath = path.resolve(frontendRoot, '../backend/docs/openapi/product-api.json')
const generatedPath = path.resolve(frontendRoot, 'src/api/generated/product.ts')
const productPathPrefix = '/api/masterdata/products'

function collectSchemaRefs(value, refs = new Set()) {
  if (Array.isArray(value)) {
    value.forEach((item) => collectSchemaRefs(item, refs))
    return refs
  }
  if (!value || typeof value !== 'object') return refs
  if (typeof value.$ref === 'string' && value.$ref.startsWith('#/components/schemas/')) {
    refs.add(decodeURIComponent(value.$ref.slice('#/components/schemas/'.length)))
  }
  Object.values(value).forEach((item) => collectSchemaRefs(item, refs))
  return refs
}

function extractProductContract(document) {
  const paths = Object.fromEntries(
    Object.entries(document.paths || {}).filter(([key]) => key.startsWith(productPathPrefix))
  )
  if (Object.keys(paths).length === 0) {
    throw new Error(`OpenAPI 中未找到 ${productPathPrefix} 路径`)
  }

  const allSchemas = document.components?.schemas || {}
  const requiredSchemas = collectSchemaRefs(paths)
  const queue = [...requiredSchemas]
  for (let i = 0; i < queue.length; i += 1) {
    const name = queue[i]
    const schema = allSchemas[name]
    if (!schema) throw new Error(`OpenAPI 引用了不存在的 schema: ${name}`)
    for (const dependency of collectSchemaRefs(schema)) {
      if (!requiredSchemas.has(dependency)) {
        requiredSchemas.add(dependency)
        queue.push(dependency)
      }
    }
  }

  const schemas = Object.fromEntries(
    [...requiredSchemas].sort().map((name) => [name, allSchemas[name]])
  )
  return {
    openapi: document.openapi,
    info: {
      title: 'ERP Product API',
      version: document.info?.version || 'v0'
    },
    paths,
    components: { schemas }
  }
}

async function generateText(document) {
  const nodes = await openapiTS(document, { alphabetize: true })
  return `${COMMENT_HEADER}${astToString(nodes)}`
}

async function loadArtifact() {
  return JSON.parse(await readFile(artifactPath, 'utf8'))
}

async function refreshArtifact() {
  const sourceUrl = process.env.OPENAPI_URL || 'http://127.0.0.1:8080/v3/api-docs'
  const response = await fetch(sourceUrl)
  if (!response.ok) throw new Error(`读取 OpenAPI 失败: ${response.status} ${response.statusText}`)
  const contract = extractProductContract(await response.json())
  await mkdir(path.dirname(artifactPath), { recursive: true })
  await writeFile(artifactPath, `${JSON.stringify(contract, null, 2)}\n`)
  return contract
}

async function writeGenerated(document) {
  await mkdir(path.dirname(generatedPath), { recursive: true })
  await writeFile(generatedPath, await generateText(document))
}

const mode = process.argv[2] || 'generate'
if (mode === 'refresh') {
  const document = await refreshArtifact()
  await writeGenerated(document)
  console.log(`已刷新 ${path.relative(frontendRoot, artifactPath)} 和 ${path.relative(frontendRoot, generatedPath)}`)
} else if (mode === 'generate') {
  await writeGenerated(await loadArtifact())
  console.log(`已生成 ${path.relative(frontendRoot, generatedPath)}`)
} else if (mode === 'check') {
  const expected = await generateText(await loadArtifact())
  const actual = await readFile(generatedPath, 'utf8').catch(() => '')
  if (actual !== expected) {
    throw new Error('OpenAPI 生成类型已漂移，请运行 npm run openapi:generate 并提交结果')
  }
  console.log('OpenAPI 产品类型与版本化契约一致')
} else {
  throw new Error(`未知模式: ${mode}`)
}
