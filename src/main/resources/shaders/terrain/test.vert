#version 330

uniform mat4 modelViewProjectionMatrix;
uniform mat4 modelViewMatrix;
uniform mat4 normalMatrix;
uniform float heightScale;
uniform float uvOffset;
uniform float uvScale;
uniform sampler2D heightMapTexture;

in vec2 position;
in vec2 uv;

out VertexData {
    vec3 position;
//    vec3 mvmNormal;
//    vec3 normal;
    vec3 eye;
    vec2 uv;
} VertexOut;

void main () {
//    vec2 size = vec2(0.03,0.0);
    VertexOut.uv = uv;
    float height = texture(heightMapTexture, uv).r * heightScale;
//    float s01 = texture(heightMapTexture, uv + vec2(-uvOffset, 0)).x * heightScale;
//    float s21 = texture(heightMapTexture, uv + vec2(+uvOffset, 0)).x * heightScale;
//    float s10 = texture(heightMapTexture, uv + vec2(0, -uvOffset)).x * heightScale;
//    float s12 = texture(heightMapTexture, uv + vec2(0, uvOffset)).x * heightScale;
//    vec3 va = normalize(vec3(size.xy,s21-s01));
//    vec3 vb = normalize(vec3(size.yx,s12-s10));
//    vec4 bump = vec4( cross(va,vb), height);
    vec4 pos4 = vec4(position, height, 1.0);
    vec4 mvmPos = modelViewMatrix * pos4;
    VertexOut.position = vec3(mvmPos);
//    vec3 normal = -vec3(bump);
//    VertexOut.mvmNormal = vec3(modelViewMatrix * vec4(normal, 0.0));
//    VertexOut.normal = vec3(normalize(normalMatrix * vec4(normal, 0.0)));
    VertexOut.eye = vec3(-mvmPos);
    gl_Position = modelViewProjectionMatrix * pos4;
}