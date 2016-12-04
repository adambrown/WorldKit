#version 330

uniform mat4 modelViewProjectionMatrix;
uniform mat4 modelViewMatrix;
uniform mat4 normalMatrix;
uniform vec4 lightPosition;
uniform vec4 color;
uniform vec4 ambientColor;
uniform vec4 diffuseColor;
uniform vec4 specularColor;
uniform float shininess;
uniform float heightScale;
uniform float uvOffset;
uniform float uvScale;
uniform sampler2D heightMapTexture;


in VertexData {
    vec3 position;
//    vec3 mvmNormal;
//    vec3 normal;
    vec3 eye;
    vec2 uv;
} VertexIn;

layout(location = 0) out vec4 colorOut;

const vec2 size = vec2(2.0,0.0);
const ivec3 off = ivec3(-1,0,1);

void main() {

    float height = texture(heightMapTexture, VertexIn.uv).r * heightScale;
    float s01 = textureOffset(heightMapTexture, VertexIn.uv, off.xy).r * heightScale;
    float s21 = textureOffset(heightMapTexture, VertexIn.uv, off.zy).r * heightScale;
    float s10 = textureOffset(heightMapTexture, VertexIn.uv, off.yx).r * heightScale;
    float s12 = textureOffset(heightMapTexture, VertexIn.uv, off.yz).r * heightScale;
    vec3 va = vec3(size.xy,s21-s01);
    vec3 vb = vec3(size.yx,s12-s10);
    vec3 bump = -normalize(cross(va,vb));
    vec3 mvmNormal = vec3(modelViewMatrix * vec4(bump, 0.0));
    vec3 normal = vec3(normalize(normalMatrix * vec4(bump, 0.0)));



    vec3 lightDir = vec3(lightPosition) - VertexIn.position;
    float ligthDistance = length(lightDir);
    vec3 lightVector = normalize(lightDir);
    float diffuse;
    if (gl_FrontFacing) {
        diffuse = max(dot(mvmNormal, lightVector), 0.0);
    } else {
        diffuse = max(dot(-mvmNormal, lightVector), 0.0);
    }
    diffuse = diffuse * (1.0 / (1.0 + (0.05 * ligthDistance)));
    diffuse = diffuse + 0.3;
    vec4 spec = vec4(0.0);
//    vec3 normal;
    if (gl_FrontFacing) {
        normal = normalize(normal);
    } else {
        normal = normalize(-normal);
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