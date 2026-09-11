import SwaggerParser from '@apidevtools/swagger-parser';
const spec = await SwaggerParser.validate('openapi/grantledger-v1.yaml');
const operations = Object.entries(spec.paths).flatMap(([path, item]) =>
  Object.entries(item).filter(([method]) => ['get', 'post', 'put', 'patch', 'delete'].includes(method)).map(([method, operation]) => ({path, method, operation})));
if (operations.length !== 16) throw new Error(`Expected 16 operations; found ${operations.length}`);
if (new Set(operations.map(({operation}) => operation.operationId)).size !== 16) throw new Error('Operation IDs must be unique');
for (const {path, operation} of operations) {
  if (!path.startsWith('/api/v1/')) throw new Error(`Unversioned path: ${path}`);
  if (!operation.security?.length) throw new Error(`Missing security: ${path}`);
  for (const status of ['400', '401', '403', '404', '409', '503']) if (!operation.responses[status]) throw new Error(`Missing ${status}: ${path}`);
}
console.log('Validated OpenAPI and all 16 secured operations.');
