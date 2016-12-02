#version 410

uniform vec4 lightPosition;
uniform vec4 color;
uniform vec4 ambientColor;
uniform vec4 diffuseColor;
uniform vec4 specularColor;
uniform float shininess;

in VertexData {
    vec3 position;
    vec3 mvmNormal;
    vec3 normal;
    vec3 eye;
} VertexIn;

layout(location = 0) out vec4 colorOut;

void main() {
    vec3 lightDir = vec3(lightPosition) - VertexIn.position;
    float ligthDistance = length(lightDir);
    vec3 lightVector = normalize(lightDir);
    float diffuse;
    if (gl_FrontFacing) {
        diffuse = max(dot(VertexIn.mvmNormal, lightVector), 0.0);
    } else {
        diffuse = max(dot(-VertexIn.mvmNormal, lightVector), 0.0);
    }
    diffuse = diffuse * (1.0 / (1.0 + (0.05 * ligthDistance)));
    diffuse = diffuse + 0.3;
    vec4 spec = vec4(0.0);
    vec3 normal;
    if (gl_FrontFacing) {
        normal = normalize(VertexIn.normal);
    } else {
        normal = normalize(-VertexIn.normal);
    }
    vec3 eye = normalize(VertexIn.eye);
    float intensity = max(dot(normal, lightVector), 0.0);
    if (intensity > 0.0) {
        vec3 halfVector = normalize(lightVector + eye);
        float intSpec = max(dot(halfVector, normal), 0.0);
        spec = specularColor * pow(intSpec, shininess);
    }
    colorOut = color * diffuse * max(intensity * diffuseColor + spec, ambientColor);
}