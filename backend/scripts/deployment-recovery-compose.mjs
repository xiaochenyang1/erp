import assert from 'node:assert/strict'
import path from 'node:path'
import { recoveryProjectPattern } from './recovery-checkpoint.mjs'

export function addRecoveryStores(configuration, project) {
  assert(recoveryProjectPattern.test(project || ''))
  for (const target of ['restore', 'rollback']) {
    for (const name of ['mysql-data', 'redis-data', 'attachments']) {
      const key = `${target}-${name}`
      assert(!configuration.volumes[key], 'Recovery volumes must be new')
      configuration.volumes[key] = { name: `${project}_${key}` }
    }
    for (const name of ['mysql', 'redis']) {
      const key = `${name}-${target}`
      assert(!configuration.services[key], 'Recovery services must be new')
      const service = structuredClone(configuration.services[name])
      assert(!service.ports?.length, 'Data services cannot publish ports')
      assert.deepEqual(Object.keys(service.networks), ['default'])
      const data = service.volumes.filter(volume => volume.type === 'volume')
      assert.equal(data.length, 1)
      assert.equal(data[0].source, `${name}-data`)
      data[0].source = `${target}-${name}-data`
      for (const volume of service.volumes) if (volume.target === '/backups') volume.read_only = true
      configuration.services[key] = service
    }
  }
}

// Always derive both switches from the original application configuration. A
// second replacement of the already-upgraded JDBC URL can silently reuse V169.
export function recoveryApplication(source, { target, project, buildContext }) {
  assert(['restore', 'rollback'].includes(target), 'Unknown recovery target')
  assert(recoveryProjectPattern.test(project || ''))
  assert.equal(source.image, `${project}-legacy`)
  assert(!source.ports?.length)
  assert.deepEqual(Object.keys(source.networks), ['default'])
  assert.equal(source.environment.SPRING_PROFILES_ACTIVE, 'prod')
  assert(!source.environment.SPRING_FLYWAY_TARGET && source.environment.SPRING_FLYWAY_ENABLED !== 'false')
  assert.match(source.environment.ERP_DATASOURCE_URL, /^jdbc:mysql:\/\/mysql:3306\/erp_server(?:\?|$)/)
  const app = structuredClone(source)
  if (target === 'restore') {
    assert(path.isAbsolute(buildContext || ''), 'Candidate build context must be absolute')
    app.build.context = buildContext
  }
  app.image = `${project}-${target === 'restore' ? 'candidate' : 'legacy'}`
  app.environment.ERP_DATASOURCE_URL = source.environment.ERP_DATASOURCE_URL.replace('mysql:3306', `mysql-${target}:3306`)
  app.environment.ERP_REDIS_HOST = `redis-${target}`
  app.depends_on = { [`mysql-${target}`]: { condition: 'service_healthy' }, [`redis-${target}`]: { condition: 'service_healthy' } }
  assert.equal(app.volumes.length, 1)
  assert.equal(app.volumes[0].type, 'volume')
  assert.equal(app.volumes[0].source, 'attachments')
  app.volumes[0].source = `${target}-attachments`
  return app
}
