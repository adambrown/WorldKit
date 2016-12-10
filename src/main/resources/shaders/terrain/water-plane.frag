#version 330

uniform mat4 modelViewMatrix;
uniform vec3 lightDirection;
uniform vec4 color;
uniform vec4 ambientColor;
uniform vec4 diffuseColor;
uniform vec4 specularColor;
uniform float shininess;


in VertexData {
    vec3 position;
    vec3 normal;
} VertexIn;

layout(location = 0) out vec4 colorOut;

const float gamma = 2.2;

vec3 toLinear(vec3 v) {
  return pow(v, vec3(gamma));
}

vec4 toLinear(vec4 v) {
  return vec4(toLinear(v.rgb), v.a);
}

vec3 toGamma(vec3 v) {
  return pow(v, vec3(1.0 / gamma));
}

vec4 toGamma(vec4 v) {
  return vec4(toGamma(v.rgb), v.a);
}

void main() {
    if (!gl_FrontFacing) {
        colorOut = vec4(0.076f, 0.082f, 0.212f, 1.0f);
        return;
    }
    vec3 normal = normalize(VertexIn.normal);
    float intensity = max(dot(normal, lightDirection), 0.0);
    float diffuse = intensity + 0.3;
    vec4 baseColor = toLinear(color);
    vec4 lightColor = toLinear(vec4(1.0));
    vec4 diffuseColor = vec4(baseColor.rgb * lightColor.rgb * diffuse, 1.0);
    vec4 spec = vec4(0.0);
    if (intensity > 0.0) {
        vec3 eye = -normalize(vec3(modelViewMatrix * vec4(VertexIn.position, 1.0)));
        vec3 halfVector = normalize(lightDirection + eye);
        spec = toLinear(specularColor) * pow(max(dot(halfVector, normal), 0.0), shininess);
    }
    colorOut = toGamma(diffuseColor * max(intensity * toLinear(diffuseColor) + spec, toLinear(ambientColor)));
}
