package dev.hekate.vectortiles.dto


import com.fasterxml.jackson.annotation.JsonProperty

data class Service(
    @JsonProperty("title")
    var title: String = "",
    @JsonProperty("description")
    var description: String = "",
    @JsonProperty("tags")
    val tags: String = "",
    @JsonProperty("references")
    var references: List<String> = listOf(),
    @JsonProperty("links")
    var links: List<Link>? = listOf()
)

data class Link(
  @JsonProperty("href")
  val href: String = "",
  @JsonProperty("rel")
  val rel: String? = "",
  @JsonProperty("title")
  val title: String? = ""
)
