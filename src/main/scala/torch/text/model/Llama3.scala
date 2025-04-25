/////usr/bin/env jbang "$0" "$@" ; exit $?
////JAVA 21+
////PREVIEW
////COMPILE_OPTIONS --add-modules=jdk.incubator.vector
////RUNTIME_OPTIONS --add-modules=jdk.incubator.vector -Djdk.incubator.vector.VECTOR_ACCESS_OOB_CHECK=0
////MAIN com.llama4j.Llama3
//// Practical Llama 3 (and 3.1) inference in a single Java file
//// Author: Alfonso² Peterssen
//// Based on Andrej Karpathy's llama2.c and minbpe projects
////
//// Supports llama.cpp's GGUF format, restricted to Q4_0 and Q8_0 quantized models
//// Multi-threaded matrix vector multiplication routines implemented using Java's Vector API
//// Simple CLI with --chat and --instruct mode
////
//// To run just:
//// jbang Llama3.java --help
////
//// Enjoy!
//package torch.text.model
//
//import jdk.incubator.vector._
//import sun.misc.Unsafe
//import java.io.IOException
//import java.io.PrintStream
//import java.lang.foreign.Arena
//import java.lang.foreign.MemorySegment
//import java.lang.foreign.ValueLayout
//import java.lang.reflect.Field
//import java.nio.ByteBuffer
//import java.nio.ByteOrder
//import java.nio.FloatBuffer
//import java.nio.channels.FileChannel
//import java.nio.charset.StandardCharsets
//import java.nio.file.Files
//import java.nio.file.Path
//import java.nio.file.Paths
//import java.nio.file.StandardOpenOption
//import java.util._
//import java.util.concurrent.TimeUnit
//import java.util.function.IntConsumer
//import java.util.function.IntFunction
//import java.util.function.LongConsumer
//import java.util.random.RandomGenerator
//import java.util.random.RandomGeneratorFactory
//import java.util.regex.Matcher
//import java.util.regex.Pattern
//import java.util.stream.Collectors
//import java.util.stream.IntStream
//import java.util.stream.LongStream
//import java.util.stream.Stream
//
//object Llama3 {
//  // Batch-size used in prompt evaluation.
//  private val BATCH_SIZE = Integer.getInteger("llama.BatchSize", 16)
//
//  def selectSampler(vocabularySize: Int, temperature: Float, topp: Float, rngSeed: Long) = {
//    var sampler: Sampler = null
//    if (temperature == 0.0f) {
//      // greedy argmax sampling: take the token with the highest probability
//      sampler = Sampler.ARGMAX
//    }
//    else {
//      // we sample from this distribution to get the next token
//      val rng = RandomGeneratorFactory.getDefault.create(rngSeed)
//      var innerSampler: Sampler = null
//      if (topp <= 0 || topp >= 1) {
//        // simply sample from the predicted probability distribution
//        innerSampler = new CategoricalSampler(rng)
//      }
//      else {
//        // top-p (nucleus) sampling, clamping the least likely tokens to zero
//        innerSampler = new ToppSampler(vocabularySize, topp, rng)
//      }
//      sampler = (logits: FloatTensor) => {
//
//        // apply the temperature to the logits
//        logits.divideInPlace(0, logits.size, temperature)
//        // apply softmax to the logits to get the probabilities for next token
//        logits.softmaxInPlace(0, logits.size)
//        innerSampler.sampleToken(logits)
//      }
//    }
//    sampler
//  }
//
//  def runInteractive(model: Llama, sampler: Sampler, options: Llama3.Options): Unit = {
//    var state: Llama.State = null
//    val conversationTokens = new util.ArrayList[Integer]
//    val chatFormat = new ChatFormat(model.tokenizer)
//    conversationTokens.add(chatFormat.beginOfText)
//    if (options.systemPrompt != null) conversationTokens.addAll(chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.SYSTEM, options.systemPrompt)))
//    var startPosition = 0
//    val in = new Scanner(System.in)
//    loop //todo: labels are not supported
//    while (true) {
//      System.out.print("> ")
//      System.out.flush()
//      val userText = in.nextLine
//      userText match {
//        case "/quit" =>
//        case "/exit" =>
//          break loop // todo: label break is not supported
//        case "/context" =>
//          System.out.printf("%d out of %d context tokens used (%d tokens remaining)%n", conversationTokens.size, options.maxTokens, options.maxTokens - conversationTokens.size)
//          continue //todo: continue is not supported
//      }
//      if (state == null) state = model.createNewState(BATCH_SIZE)
//      conversationTokens.addAll(chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.USER, userText)))
//      conversationTokens.addAll(chatFormat.encodeHeader(new ChatFormat.Message(ChatFormat.Role.ASSISTANT, "")))
//      val stopTokens = chatFormat.getStopTokens
//      val responseTokens = Llama.generateTokens(model, state, startPosition, conversationTokens.subList(startPosition, conversationTokens.size), stopTokens, options.maxTokens, sampler, options.echo, (token: Int) => {
//        if (options.stream) if (!model.tokenizer.isSpecialToken(token)) System.out.print(model.tokenizer.decode(util.List.of(token)))
//      })
//      // Include stop token in the prompt history, but not in the response displayed to the user.
//      conversationTokens.addAll(responseTokens)
//      startPosition = conversationTokens.size
//      var stopToken: Integer = null
//      if (!responseTokens.isEmpty && stopTokens.contains(responseTokens.getLast)) {
//        stopToken = responseTokens.getLast
//        responseTokens.removeLast
//      }
//      if (!options.stream) {
//        val responseText = model.tokenizer.decode(responseTokens)
//        System.out.println(responseText)
//      }
//      if (stopToken == null) {
//        System.err.println("Ran out of context length...")
//        break //todo: break is not supported
//      }
//    }
//  }
//
//  def runInstructOnce(model: Llama, sampler: Sampler, options: Llama3.Options): Unit = {
//    val state = model.createNewState(BATCH_SIZE)
//    val chatFormat = new ChatFormat(model.tokenizer)
//    val promptTokens = new util.ArrayList[Integer]
//    promptTokens.add(chatFormat.beginOfText)
//    if (options.systemPrompt != null) promptTokens.addAll(chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.SYSTEM, options.systemPrompt)))
//    promptTokens.addAll(chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.USER, options.prompt)))
//    promptTokens.addAll(chatFormat.encodeHeader(new ChatFormat.Message(ChatFormat.Role.ASSISTANT, "")))
//    val stopTokens = chatFormat.getStopTokens
//    val responseTokens = Llama.generateTokens(model, state, 0, promptTokens, stopTokens, options.maxTokens, sampler, options.echo, (token: Int) => {
//      if (options.stream) if (!model.tokenizer.isSpecialToken(token)) System.out.print(model.tokenizer.decode(util.List.of(token)))
//    })
//    if (!responseTokens.isEmpty && stopTokens.contains(responseTokens.getLast)) responseTokens.removeLast
//    if (!options.stream) {
//      val responseText = model.tokenizer.decode(responseTokens)
//      System.out.println(responseText)
//    }
//  }
//  object Options {
//    private val DEFAULT_MAX_TOKENS = 512
//
//    private def require(condition: Boolean, messageFormat: String, args: AnyRef*): Unit = {
//      if (!condition) {
//        System.out.println("ERROR " + messageFormat.formatted(args))
//        System.out.println()
//        printUsage(System.out)
//        System.exit(-1)
//      }
//    }
//
//    def printUsage(out: PrintStream): Unit = {
//      out.println("Usage:  jbang Llama3.java [options]")
//      out.println()
//      out.println("Options:")
//      out.println("  --model, -m <path>            required, path to .gguf file")
//      out.println("  --interactive, --chat, -i     run in chat mode")
//      out.println("  --instruct                    run in instruct (once) mode, default mode")
//      out.println("  --prompt, -p <string>         input prompt")
//      out.println("  --system-prompt, -sp <string> (optional) system prompt")
//      out.println("  --temperature, -temp <float>  temperature in [0,inf], default 0.1")
//      out.println("  --top-p <float>               p value in top-p (nucleus) sampling in [0,1] default 0.95")
//      out.println("  --seed <long>                 random seed, default System.nanoTime()")
//      out.println("  --max-tokens, -n <int>        number of steps to run for < 0 = limited by context length, default " + DEFAULT_MAX_TOKENS)
//      out.println("  --stream <boolean>            print tokens during generation; may cause encoding artifacts for non ASCII text, default true")
//      out.println("  --echo <boolean>              print ALL tokens to stderr, if true, recommended to set --stream=false, default false")
//      out.println()
//      out.println("Examples:")
//      out.println("  jbang Llama3.java --model llama3.2-1b-q4_0.gguf --prompt \"Tell me a joke\"")
//      out.println("  jbang Llama3.java --model llama3.2-1b-q4_0.gguf --system-prompt \"Reply concisely, in French\" --prompt \"Who was Marie Curie?\"")
//      out.println("  jbang Llama3.java --model llama3.2-1b-q4_0.gguf --system-prompt \"Answer concisely\" --chat")
//      out.println("  jbang Llama3.java --model llama3.2-1b-q4_0.gguf --chat")
//      out.println("  jbang Llama3.java --model llama3.2-1b-q4_0.gguf --prompt \"Print 5 emojis\" --stream=false")
//    }
//
//    def parseOptions(args: Array[String]) = {
//      var prompt: String = null
//      var systemPrompt: String = null
//      var temperature = 0.1f
//      var topp = 0.95f
//      var modelPath: Path = null
//      var seed = System.nanoTime
//      // Keep max context length small for low-memory devices.
//      var maxTokens = DEFAULT_MAX_TOKENS
//      var interactive = false
//      var stream = true
//      var echo = false
//      var i = 0
//      while (i < args.length) {
//        var optionName = args(i)
//        require(optionName.startsWith("-"), "Invalid option %s", optionName)
//        optionName match {
//          case "--interactive" | "--chat" | "-i" => interactive = true
//          case "--instruct" => interactive = false
//          case "--help" | "-h" =>
//            printUsage(System.out)
//            System.exit(0)
//          case _ =>
//            var nextArg: String = null
//            if (optionName.contains("=")) {
//              val parts = optionName.split("=", 2)
//              optionName = parts(0)
//              nextArg = parts(1)
//            }
//            else {
//              require(i + 1 < args.length, "Missing argument for option %s", optionName)
//              nextArg = args(i + 1)
//              i += 1 // skip arg
//            }
//            optionName match {
//              case "--prompt" | "-p" => prompt = nextArg
//              case "--system-prompt" | "-sp" => systemPrompt = nextArg
//              case "--temperature" | "--temp" => temperature = Float.parseFloat(nextArg)
//              case "--top-p" => topp = Float.parseFloat(nextArg)
//              case "--model" | "-m" => modelPath = Paths.get(nextArg)
//              case "--seed" | "-s" => seed = Long.parseLong(nextArg)
//              case "--max-tokens" | "-n" => maxTokens = nextArg.toInt
//              case "--stream" => stream = Boolean.parseBoolean(nextArg)
//              case "--echo" => echo = Boolean.parseBoolean(nextArg)
//              case _ => require(false, "Unknown option: %s", optionName)
//            }
//        }
//        i += 1
//      }
//      new Llama3.Options(modelPath, prompt, systemPrompt, interactive, temperature, topp, seed, maxTokens, stream, echo)
//    }
//  }
//
//  final  class Options (
//
//  :
//  Path
//  ,:
//  String
//  ,:
//  String
//  ,:
//  Boolean
//  ,:
//  Float
//  ,:
//  Float
//  ,:
//  Long
//  ,:
//  Int
//  ,:
//  Boolean
//  ,:
//  Boolean
//  )
//  {
//    Options.require(modelPath != null, "Missing argument: --model <path> is required")
//    Options.require(interactive || prompt != null, "Missing argument: --prompt is required in --instruct mode e.g. --prompt \"Why is the sky blue?\"")
//    Options.require(0 <= temperature, "Invalid argument: --temperature must be non-negative")
//    Options.require(0 <= topp && topp <= 1, "Invalid argument: --top-p must be within [0, 1]")
//    final private val modelPath: Path = null
//    final private val prompt: String = null
//    final private val systemPrompt: String = null
//    final private val interactive = false
//    final private val temperature = .0
//    final private val topp = .0
//    final private val seed = 0L
//    final private val maxTokens = 0
//    final private val stream = false
//    final private val echo = false
//  }
//
//  @throws[IOException]
//  def main(args: Array[String]): Unit = {
//    val options = Options.parseOptions(args)
//    var model = AOT.tryUsePreLoaded(options.modelPath, options.maxTokens)
//    if (model == null) {
//      // No compatible preloaded model found, fallback to fully parse and load the specified file.
//      model = ModelLoader.loadModel(options.modelPath, options.maxTokens, true)
//    }
//    val sampler = selectSampler(model.configuration.vocabularySize, options.temperature, options.topp, options.seed)
//    if (options.interactive) runInteractive(model, sampler, options)
//    else runInstructOnce(model, sampler, options)
//  }
//}
//
//object GGUF {
//  private val GGUF_MAGIC = 0x46554747
//  private val DEFAULT_ALIGNMENT = 32 // must be a power of 2
//  private val SUPPORTED_GGUF_VERSIONS = util.List.of(2, 3)
//
//  @throws[IOException]
//  def loadModel(modelPath: Path): GGUF = try {
//    val fileChannel = FileChannel.open(modelPath)
//    val ignored = Timer.log("Parse " + modelPath)
//    try {
//      val gguf = new GGUF
//      gguf.loadModelImpl(fileChannel)
//      gguf
//    } finally {
//      if (fileChannel != null) fileChannel.close()
//      if (ignored != null) ignored.close()
//    }
//  }
//
//   object MetadataValueType extends util.Enumeration {
//    type MetadataValueType = Value
//    val
//    // The value is a 8-bit unsigned integer.
//    UINT8, // The value is a 8-bit signed integer.
//    INT8, // The value is a 16-bit unsigned little-endian integer.
//    UINT16, // The value is a 16-bit signed little-endian integer.
//    INT16, // The value is a 32-bit unsigned little-endian integer.
//    UINT32, // The value is a 32-bit signed little-endian integer.
//    INT32, // The value is a 32-bit IEEE754 floating point number.
//    FLOAT32, // The value is a boolean.
//    // 1-byte value where 0 is false and 1 is true.
//    // Anything else is invalid, and should be treated as either the model being invalid or the reader being buggy.
//    BOOL, // The value is a UTF-8 non-null-terminated string, with length prepended.
//    STRING, // The value is an array of other values, with the length and type prepended.
//    // Arrays can be nested, and the length of the array is the number of elements in the array, not the number of bytes.
//    ARRAY, // The value is a 64-bit unsigned little-endian integer.
//    UINT64, // The value is a 64-bit signed little-endian integer.
//    INT64, // The value is a 64-bit IEEE754 floating point number.
//    FLOAT64 = Value
//    private val byteSize = 0d ef this (byteSize: Int) {
//      this ()
//      this.byteSize = byteSize
//    }
//    private val VALUES = valuesdef fromIndex (index: Int): GGUF.MetadataValueType
//    =
//    {
//      return VALUES(index)
//    }
//
//    def byteSize: Int = byteSize
//  }
//
//  @throws[IOException]
//  def loadTensors(fileChannel: FileChannel, tensorDataOffset: Long, tensorInfos: util.Map[String, GGUF.GGUFTensorInfo]): util.Map[String, GGMLTensorEntry] = {
//    val arena = Arena.ofAuto
//    val tensorData = fileChannel.map(FileChannel.MapMode.READ_ONLY, tensorDataOffset, fileChannel.size - tensorDataOffset, arena)
//    val tensorEntries = util.HashMap.newHashMap(tensorInfos.size)
//    import scala.collection.JavaConversions._
//    for (entry <- tensorInfos.entrySet) {
//      val ti = entry.getValue
//      val numberOfElements = FloatTensor.numberOfElements(ti.dimensions)
//      val sizeInBytes = Math.toIntExact(ti.ggmlType.byteSizeFor(numberOfElements))
//      val memorySegment = tensorData.asSlice(ti.offset, sizeInBytes)
//      tensorEntries.put(ti.name, new GGMLTensorEntry(tensorData, ti.name, ti.ggmlType, ti.dimensions, memorySegment))
//    }
//    tensorEntries
//  }
//
//  final class GGUFTensorInfo(name: String, dimensions: Array[Int], ggmlType: GGMLType, offset: Long) {
//    this.name = name
//    this.dimensions = dimensions
//    this.ggmlType = ggmlType
//    this.offset = offset
//    final private val name: String = null
//    final private val dimensions: Array[Int] = null
//    final private val ggmlType: GGMLType = null
//    final private val offset = 0L
//  }
//}
//
//final class GGUF {
//  private var magic = 0
//  private var version = 0
//  private var tensorCount = 0 // uint64_t
//  private var alignment = 0
//  private var metadata_kv_count = 0 // uint64_t
//  private var metadata: util.Map[String, AnyRef] = null
//
//  def getTensorInfos: util.Map[String, GGUF.GGUFTensorInfo] = tensorInfos
//
//  private var tensorInfos: util.Map[String, GGUF.GGUFTensorInfo] = null
//  private var tensorDataOffset = 0L
//
//  def getTensorDataOffset: Long = tensorDataOffset
//
//  def getMetadata: util.Map[String, AnyRef] = metadata
//
//  final private val BB_1 = ByteBuffer.allocate(Byte.BYTES).order(ByteOrder.LITTLE_ENDIAN)
//  final private val BB_2 = ByteBuffer.allocate(Short.BYTES).order(ByteOrder.LITTLE_ENDIAN)
//  final private val BB_4 = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN)
//  final private val BB_8 = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN)
//
//  @throws[IOException]
//  private def loadModelImpl(fileChannel: FileChannel): Unit = {
//    // The header of the file.
//    readHeader(fileChannel) // gguf_header_t header;
//
//    // Tensor infos, which can be used to locate the tensor data.
//    // gguf_tensor_info_t tensor_infos[header.tensor_count];
//    this.tensorInfos = util.HashMap.newHashMap(tensorCount)
//    for (i <- 0 until tensorCount) {
//      val ti = readTensorInfo(fileChannel)
//      assert(!tensorInfos.containsKey(ti.name))
//      tensorInfos.put(ti.name, ti)
//    }
//    // Padding to the nearest multiple of `ALIGNMENT`.
//    // uint8_t _padding[ALIGNMENT - (sizeof(header + tensor_infos) % ALIGNMENT)];
//    //long _padding = -fileChannel.position() & (ALIGNMENT - 1);
//    val _padding = getAlignment - (fileChannel.position % getAlignment)
//    fileChannel.position(fileChannel.position + _padding)
//    // Tensor data.
//    //
//    // This is arbitrary binary data corresponding to the weights of the model. This data should be close
//    // or identical to the data in the original model file, but may be different due to quantization or
//    // other optimizations for inference. Any such deviations should be recorded in the metadata or as
//    // part of the architecture definition.
//    //
//    // Each tensor's data must be stored within this array, and located through its `tensor_infos` entry.
//    // The offset of each tensor's data must be a multiple of `ALIGNMENT`, and the space between tensors
//    // should be padded to `ALIGNMENT` bytes.
//    // uint8_t tensor_data[];
//    this.tensorDataOffset = fileChannel.position
//  }
//
//  @throws[IOException]
//  private def readGGMLType(fileChannel: FileChannel) = {
//    val ggmlTypeId = readInt(fileChannel) // ggml_type type;
//    GGMLType.fromId(ggmlTypeId)
//  }
//
//  @throws[IOException]
//  private def readTensorInfo(fileChannel: FileChannel) = {
//    // The name of the tensor. It is a standard GGUF string, with the caveat that
//    // it must be at most 64 bytes long.
//    val name = readString(fileChannel) // gguf_string_t name;
//    assert(name.length <= 64)
//    // The number of dimensions in the tensor.
//    // Currently at most 4, but this may change in the future.
//    val n_dimensions = readInt(fileChannel) // uint32_t n_dimensions;
//    assert(n_dimensions <= 4)
//    // The dimensions of the tensor.
//    val dimensions = new Array[Int](n_dimensions) // uint64_t dimensions[n_dimensions];
//    for (i <- 0 until n_dimensions) {
//      dimensions(i) = Math.toIntExact(readLong(fileChannel))
//    }
//    // The type of the tensor.
//    val ggmlType = readGGMLType(fileChannel) // ggml_type type;
//    // The offset of the tensor's data in this file in bytes.
//    // This offset is relative to `tensor_data`, not to the start
//    // of the file, to make it easier for writers to write the file.
//    // Readers should consider exposing this offset relative to the
//    // file to make it easier to read the data.
//    // Must be a multiple of `ALIGNMENT`.
//    val offset = readLong(fileChannel) // uint64_t offset;
//    assert(offset % getAlignment == 0)
//    new GGUF.GGUFTensorInfo(name, dimensions, ggmlType, offset)
//  }
//
//  @throws[IOException]
//  private def readString(fileChannel: FileChannel) = {
//    // A string in GGUF.
//    // The length of the string, in bytes.
//    val len = Math.toIntExact(readLong(fileChannel)) // uint64_t len;
//    // The string as a UTF-8 non-null-terminated string.
//    val bytes = new Array[Byte](len) // char string[len];
//    val bytesRead = fileChannel.read(ByteBuffer.wrap(bytes))
//    assert(len == bytesRead)
//    new String(bytes, StandardCharsets.UTF_8)
//  }
//
//  @throws[IOException]
//  private def readKeyValuePair(fileChannel: FileChannel) = {
//    // The key of the metadata. It is a standard GGUF string, with the following caveats:
//    // - It must be a valid ASCII string.
//    // - It must be a hierarchical key, where each segment is `lower_snake_case` and separated by a `.`.
//    // - It must be at most 2^16-1/65535 bytes long.
//    // Any keys that do not follow these rules are invalid.
//    val key = readString(fileChannel) // gguf_string_t key;
//    assert(key.length < (1 << 16))
//    assert(key.codePoints.allMatch((cp: Int) => ('a' <= cp && cp <= 'z') || ('0' <= cp && cp <= '9') || cp == '_' || cp == '.'))
//    val value = readMetadataValue(fileChannel)
//    new Pair[String, AnyRef](key, value)
//  }
//
//  @throws[IOException]
//  private def readMetadataValue(fileChannel: FileChannel) = {
//    // The type of the value.
//    // Must be one of the `gguf_metadata_value_type` values.
//    val value_type = readMetadataValueType(fileChannel) // gguf_metadata_value_type value_type;
//
//    // The value.
//    readMetadataValueOfType(value_type, fileChannel) // gguf_metadata_value_t value;
//  }
//
//  @throws[IOException]
//  def readHeader(fileChannel: FileChannel): Unit = {
//    // Magic number to announce that this is a GGUF file.
//    // Must be `GGUF` at the byte level: `0x47` `0x47` `0x55` `0x46`.
//    // Your executor might do little-endian byte order, so it might be
//    // check for 0x46554747 and letting the endianness cancel out.
//    // Consider being *very* explicit about the byte order here.
//    this.magic = readInt(fileChannel) //    uint32_t magic;
//    if (magic != GGUF.GGUF_MAGIC) throw new IllegalArgumentException("unsupported header.magic " + magic)
//    // The version of the format implemented.
//    // Must be `3` for version described in this spec.
//    //
//    // This version should only be increased for structural changes to the format.
//    // Changes that do not affect the structure of the file should instead update the metadata
//    // to signify the change.
//    this.version = readInt(fileChannel) // uint32_t version;
//    if (!GGUF.SUPPORTED_GGUF_VERSIONS.contains(version)) throw new IllegalArgumentException("unsupported header.version " + version)
//    // The number of tensors in the file.
//    // This is explicit, instead of being included in the metadata, to ensure it is always present
//    // for loading the tensors.
//    this.tensorCount = Math.toIntExact(readLong(fileChannel)) // uint64_t tensor_count;
//
//    // The number of metadata key-value pairs.
//    this.metadata_kv_count = Math.toIntExact(readLong(fileChannel)) // uint64_t metadata_kv_count;
//
//    // The metadata key-value pairs.
//    // gguf_metadata_kv_t metadata_kv[metadata_kv_count];
//    this.metadata = util.HashMap.newHashMap(metadata_kv_count)
//    for (i <- 0 until metadata_kv_count) {
//      val keyValue = readKeyValuePair(fileChannel)
//      assert(!metadata.containsKey(keyValue.first))
//      metadata.put(keyValue.first, keyValue.second)
//    }
//  }
//
//  @throws[IOException]
//  private def readArray(fileChannel: FileChannel) = {
//    // Any value type is valid, including arrays.
//    val value_type = readMetadataValueType(fileChannel) // gguf_metadata_value_type type;
//    // Number of elements, not bytes
//    val len = Math.toIntExact(readLong(fileChannel)) // uint64_t len;
//
//    // The array of values.
//    // gguf_metadata_value_t array[len];
//    value_type match {
//      case UINT8 | INT8 =>
//        val bytes = new Array[Byte](len)
//        for (i <- 0 until len) {
//          bytes(i) = readByte(fileChannel)
//        }
//        bytes
//      case UINT16 | INT16 =>
//        val shorts = new Array[Short](len)
//        for (i <- 0 until len) {
//          shorts(i) = readShort(fileChannel)
//        }
//        shorts
//      case UINT32 | INT32 =>
//        val ints = new Array[Int](len)
//        for (i <- 0 until len) {
//          ints(i) = readInt(fileChannel)
//        }
//        ints
//      case FLOAT32 =>
//        val floats = new Array[Float](len)
//        for (i <- 0 until len) {
//          floats(i) = readFloat(fileChannel)
//        }
//        floats
//      case BOOL =>
//        val booleans = new Array[Boolean](len)
//        for (i <- 0 until len) {
//          booleans(i) = readBoolean(fileChannel)
//        }
//        booleans
//      case STRING =>
//        val strings = new Array[String](len)
//        for (i <- 0 until len) {
//          strings(i) = readString(fileChannel)
//        }
//        strings
//      case ARRAY =>
//        val arrays = new Array[AnyRef](len)
//        for (i <- 0 until len) {
//          arrays(i) = readArray(fileChannel)
//        }
//        arrays
//      case _ => throw new UnsupportedOperationException("read array of " + value_type)
//    }
//  }
//
//  @throws[IOException]
//  private def readMetadataValueOfType(valueType: GGUF.MetadataValueType, fileChannel: FileChannel) = valueType match {
//    case UINT8 | INT8 => readByte(fileChannel)
//    case UINT16 | INT16 => readShort(fileChannel)
//    case UINT32 | INT32 => readInt(fileChannel)
//    case FLOAT32 => readFloat(fileChannel)
//    case UINT64 | INT64 => readLong(fileChannel)
//    case FLOAT64 => readDouble(fileChannel)
//    case BOOL => readBoolean(fileChannel)
//    case STRING => readString(fileChannel)
//    case ARRAY => readArray(fileChannel)
//  }
//
//  @throws[IOException]
//  private def readByte(fileChannel: FileChannel) = {
//    val bytesRead = fileChannel.read(BB_1)
//    assert(bytesRead == 1)
//    BB_1.clear.get(0)
//  }
//
//  @throws[IOException]
//  private def readBoolean(fileChannel: FileChannel) = readByte(fileChannel) != 0
//
//  @throws[IOException]
//  private def readShort(fileChannel: FileChannel) = {
//    val bytesRead = fileChannel.read(BB_2)
//    assert(bytesRead == 2)
//    BB_2.clear.getShort(0)
//  }
//
//  @throws[IOException]
//  private def readInt(fileChannel: FileChannel) = {
//    val bytesRead = fileChannel.read(BB_4)
//    assert(bytesRead == 4)
//    BB_4.clear.getInt(0)
//  }
//
//  @throws[IOException]
//  private def readLong(fileChannel: FileChannel) = {
//    val bytesRead = fileChannel.read(BB_8)
//    assert(bytesRead == 8)
//    BB_8.clear.getLong(0)
//  }
//
//  @throws[IOException]
//  private def readFloat(fileChannel: FileChannel) = Float.intBitsToFloat(readInt(fileChannel))
//
//  @throws[IOException]
//  private def readDouble(fileChannel: FileChannel) = Double.longBitsToDouble(readLong(fileChannel))
//
//  @throws[IOException]
//  private def readMetadataValueType(fileChannel: FileChannel) = {
//    val index = readInt(fileChannel)
//    GGUF.MetadataValueType.fromIndex(index)
//  }
//
//  def getAlignment: Int = {
//    if (alignment != 0) return alignment
//    alignment = metadata.getOrDefault("general.alignment", GGUF.DEFAULT_ALIGNMENT).asInstanceOf[Int]
//    assert(Integer.bitCount(alignment) == 1, "alignment must be a power of two")
//    alignment
//  }
//}
//
//object Timer {
//  def log(label: String): Timer = log(label, TimeUnit.MILLISECONDS)
//
//  def log(label: String, timeUnit: TimeUnit): Timer = new Timer() {
//    final val startNanos = System.nanoTime
//
//    override def close(): Unit = {
//      val elapsedNanos = System.nanoTime - startNanos
//      System.err.println(label + ": " + timeUnit.convert(elapsedNanos, TimeUnit.NANOSECONDS) + " " + timeUnit.toChronoUnit.name.toLowerCase)
//    }
//  }
//}
//
//trait Timer extends AutoCloseable {
//  override def close(): Unit // no Exception
//}
//
//object ModelLoader {
//  private val TOKENIZER_LLAMA_3_MODEL = "gpt2"
//  private val LLAMA_3_PATTERN = "(?i:'s|'t|'re|'ve|'m|'ll|'d)|[^\\r\\n\\p{L}\\p{N}]?\\p{L}+|\\p{N}{1,3}| ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*|\\s*[\\r\\n]+|\\s+(?!\\S)|\\s+"
//
//  private def loadVocabulary(metadata: util.Map[String, AnyRef]) = {
//    val model = metadata.get("tokenizer.ggml.model").asInstanceOf[String]
//    if (!(TOKENIZER_LLAMA_3_MODEL == model)) throw new IllegalArgumentException("expected " + TOKENIZER_LLAMA_3_MODEL + " but found " + model)
//    val tokens = metadata.get("tokenizer.ggml.tokens").asInstanceOf[Array[String]]
//    new Vocabulary(tokens, null)
//  }
//
//  @throws[IOException]
//  def loadModel(ggufPath: Path, contextLength: Int, loadWeights: Boolean): Llama = {
//    val gguf = GGUF.loadModel(ggufPath)
//    val fileChannel = FileChannel.open(ggufPath, StandardOpenOption.READ)
//    loadModel(fileChannel, gguf, contextLength, loadWeights)
//  }
//
//  @throws[IOException]
//  def loadModel(fileChannel: FileChannel, gguf: GGUF, contextLength: Int, loadWeights: Boolean): Llama = try {
//    val ignored = Timer.log("Load LlaMa model")
//    try {
//      val metadata = gguf.getMetadata
//      val vocabulary = loadVocabulary(metadata)
//      val tokenizer = createTokenizer(metadata, vocabulary)
//      val config = new Llama.Configuration(metadata.get("llama.embedding_length").asInstanceOf[Int], metadata.get("llama.feed_forward_length").asInstanceOf[Int], metadata.get("llama.block_count").asInstanceOf[Int], metadata.get("llama.attention.head_count").asInstanceOf[Int], if (metadata.containsKey("llama.attention.head_count_kv")) metadata.get("llama.attention.head_count_kv").asInstanceOf[Int]
//      else metadata.get("llama.attention.head_count").asInstanceOf[Int], vocabulary.size, metadata.get("llama.context_length").asInstanceOf[Int], metadata.getOrDefault("llama.attention.layer_norm_rms_epsilon", 1e-5f).asInstanceOf[Float], metadata.getOrDefault("llama.rope.freq_base", 10000f).asInstanceOf[Float]).withContextLength(contextLength)
//      var weights: Llama.Weights = null
//      if (loadWeights) {
//        val tensorEntries = GGUF.loadTensors(fileChannel, gguf.getTensorDataOffset, gguf.getTensorInfos)
//        weights = loadWeights(tensorEntries, config)
//      }
//      new Llama(config, tokenizer, weights)
//    } finally if (ignored != null) ignored.close()
//  }
//
//  def loadWeights(tensorEntries: util.Map[String, GGMLTensorEntry], config: Llama.Configuration) = {
//    val ropeScaling = tensorEntries.containsKey("rope_freqs")
//    val scaleFactor = 8
//    val loFreqFactor = 1
//    val hiFreqFactor = 3
//    val oldContextLength = 8192
//    val ropeFreqs = RoPE.precomputeFreqsCis(config.contextLength, config.headSize, config.ropeTheta, ropeScaling, scaleFactor, loFreqFactor, hiFreqFactor, oldContextLength)
//    val ropeFreqsReal = ropeFreqs.first
//    val ropeFreqsImag = ropeFreqs.second
//    val tokenEmbeddings = tensorEntries.get("token_embd.weight")
//    val qw = new Llama.Weights(loadQuantized(tokenEmbeddings), loadArrayOfFloatBuffer(config.numberOfLayers, (i: Int) => tensorEntries.get("blk." + i + ".attn_norm.weight")), loadArrayOfQuantized(config.numberOfLayers, (i: Int) => tensorEntries.get("blk." + i + ".attn_q.weight")), loadArrayOfQuantized(config.numberOfLayers, (i: Int) => tensorEntries.get("blk." + i + ".attn_k.weight")), loadArrayOfQuantized(config.numberOfLayers, (i: Int) => tensorEntries.get("blk." + i + ".attn_v.weight")), loadArrayOfQuantized(config.numberOfLayers, (i: Int) => tensorEntries.get("blk." + i + ".attn_output.weight")), loadArrayOfFloatBuffer(config.numberOfLayers, (i: Int) => tensorEntries.get("blk." + i + ".ffn_norm.weight")), loadArrayOfQuantized(config.numberOfLayers, (i: Int) => tensorEntries.get("blk." + i + ".ffn_gate.weight")), // w1
//      loadArrayOfQuantized(config.numberOfLayers, (i: Int) => tensorEntries.get("blk." + i + ".ffn_down.weight")), // w2
//      loadArrayOfQuantized(config.numberOfLayers, (i: Int) => tensorEntries.get("blk." + i + ".ffn_up.weight")), // w3
//      toFloatBuffer(tensorEntries.get("output_norm.weight")), FloatBuffer.wrap(ropeFreqsReal), FloatBuffer.wrap(ropeFreqsImag), // If "output.weight" is not present then the embedding weights are tied/shared with the decoder.
//      // This is commonly referred as "tie word embeddings".
//      loadQuantized(tensorEntries.getOrDefault("output.weight", tokenEmbeddings)))
//    qw
//  }
//
//  private def createTokenizer(metadata: util.Map[String, AnyRef], vocabulary: Vocabulary) = {
//    val mergeLines = metadata.get("tokenizer.ggml.merges").asInstanceOf[Array[String]]
//    val merges = util.Arrays.stream(mergeLines).map((line: String) => line.split(" ")).map((parts: Array[String]) => new Pair[Integer, Integer](vocabulary.getIndex(parts(0)).orElseThrow, vocabulary.getIndex(parts(1)).orElseThrow)).toList
//    val allTokens = vocabulary.size
//    val baseTokens = 128000 // assume all tokens after the base ones are special.
//    val reservedSpecialTokens = allTokens - baseTokens
//    val specialTokensList = util.Arrays.stream(vocabulary.tokens, baseTokens, allTokens).toList
//    assert(specialTokensList.stream.allMatch((token: String) => vocabulary.getIndex(token).isPresent))
//    val specialTokens = IntStream.range(0, specialTokensList.size).boxed.collect(Collectors.toMap((i: Integer) => specialTokensList.get(i), (i: Integer) => baseTokens + i))
//    new Tokenizer(vocabulary, merges, LLAMA_3_PATTERN, specialTokens)
//  }
//
//  def loadQuantized(entry: GGMLTensorEntry): FloatTensor = {
//    val ggmlType = entry.ggmlType
//    ggmlType match {
//      //case F32 -> new F32FloatTensor(FloatTensor.numberOfElements(entry.shape()), entry.memorySegment());
//      case Q8_0 => new Q8_0FloatTensor(FloatTensor.numberOfElements(entry.shape), entry.memorySegment)
//      case Q4_0 => new Q4_0FloatTensor(FloatTensor.numberOfElements(entry.shape), entry.memorySegment)
//      case BF16 => new BF16FloatTensor(FloatTensor.numberOfElements(entry.shape), entry.memorySegment)
//      case F16 => new F16FloatTensor(FloatTensor.numberOfElements(entry.shape), entry.memorySegment)
//      case _ => throw new UnsupportedOperationException("Quantization format " + ggmlType)
//    }
//  }
//
//  def loadArrayOfQuantized(size: Int, getTensorEntry: IntFunction[GGMLTensorEntry]): Array[FloatTensor] = {
//    val array = new Array[FloatTensor](size)
//    for (i <- 0 until size) {
//      array(i) = loadQuantized(getTensorEntry.apply(i))
//    }
//    array
//  }
//
//  def loadArrayOfFloatBuffer(size: Int, getTensorEntry: IntFunction[GGMLTensorEntry]): Array[FloatBuffer] = {
//    val array = new Array[FloatBuffer](size)
//    for (i <- 0 until size) {
//      array(i) = toFloatBuffer(getTensorEntry.apply(i))
//    }
//    array
//  }
//
//  def toFloatBuffer(tensorEntry: GGMLTensorEntry): FloatBuffer = {
//    val ggmlType = tensorEntry.ggmlType
//    ggmlType match {
//      case F32 => tensorEntry.memorySegment.asByteBuffer.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer
//      case _ => throw new UnsupportedOperationException("Conversion to " + ggmlType)
//    }
//  }
//}
//
//object Llama {
//  final class Configuration(val dim: Int // transformer dimension
//                            , val hiddenDim: Int // for ffn layers
//                            , val numberOfLayers: Int // number of layers
//                            , val numberOfHeads: Int // number of query heads
//                            , val numberOfKeyValueHeads: Int // number of key/value heads (can be < query heads because of multiquery)
//                            , val vocabularySize: Int // vocabulary size, usually 256 (byte-level)
//                            , val contextLength: Int // max sequence length
//                            , val rmsNormEps: Float, val ropeTheta: Float) {
//    this.headSize = dim / numberOfHeads
//    final var headSize = 0
//
//    def withContextLength(newContextLength: Int): Llama.Configuration = {
//      if (newContextLength < 0) return this // no change
//      new Llama.Configuration(this.dim, this.hiddenDim, this.numberOfLayers, this.numberOfHeads, this.numberOfKeyValueHeads, this.vocabularySize, newContextLength, this.rmsNormEps, this.ropeTheta)
//    }
//  }
//
//  final class Weights(
//                       // token embedding table
//                       val token_embedding_table: FloatTensor // (vocab_size, dim)
//                       , // weights for rmsnorms
//                       val rms_att_weight: Array[FloatBuffer] // (layer, dim) rmsnorm weights
//                       , // weights for matmuls
//                       val wq: Array[FloatTensor] // (layer, n_heads * head_size)
//                       , val wk: Array[FloatTensor] // (layer, n_kv_heads, head_size)
//                       , val wv: Array[FloatTensor] // (layer, n_kv_heads * head_size)
//                       , val wo: Array[FloatTensor] // (layer, n_heads * head_size, dim)
//                       , val rms_ffn_weight: Array[FloatBuffer] // (layer, dim)
//                       , // weights for ffn
//                       val w1: Array[FloatTensor] // (layer, hidden_dim, dim)
//                       , val w2: Array[FloatTensor] // (layer, dim, hidden_dim)
//                       , val w3: Array[FloatTensor] // (layer, hidden_dim, dim)
//                       , // public final rmsnorm
//                       val rms_final_weight: FloatBuffer // (dim,)
//                       , // freq_cis for RoPE relatively positional embeddings
//                       val freq_cis_real: FloatBuffer // (seq_len, head_size/2)
//                       , val freq_cis_imag: FloatBuffer // (seq_len, head_size/2)
//                       , // (optional) classifier weights for the logits, on the last layer
//                       val wcls: FloatTensor // (vocab_size, dim)
//                     ) {
//  }
//
//  final class State (config: Llama.Configuration,
//                                   // current wave of activations
//                                   val batchsize: Int) {
//    this.x = allocate(batchsize, config.dim)
//    this.xb = allocate(batchsize, config.dim)
//    this.xb2 = allocate(batchsize, config.dim)
//    this.hb = allocate(batchsize, config.hiddenDim)
//    this.hb2 = allocate(batchsize, config.hiddenDim)
//    this.q = allocate(batchsize, config.dim)
//    this.k = allocate(batchsize, config.dim)
//    this.v = allocate(batchsize, config.dim)
//    this.att = allocate(batchsize, config.numberOfHeads, config.contextLength)
//    idxPrevBlock = -1
//    this.logits = ArrayFloatTensor.allocate(config.vocabularySize)
//    val kvDim: Int = (config.dim * config.numberOfKeyValueHeads) / config.numberOfHeads
//    this.keyCache = Stream.generate(() => ArrayFloatTensor.allocate(config.contextLength, kvDim)).limit(config.numberOfLayers).toArray(`new`)
//    this.valueCache = Stream.generate(() => ArrayFloatTensor.allocate(config.contextLength, kvDim)).limit(config.numberOfLayers).toArray(`new`)
//    final var x: Array[FloatTensor] = null // activation at current time stamp (dim,)
//    final var xb: Array[FloatTensor] = null // same, but inside a residual branch (dim,)
//    final var xb2: Array[FloatTensor] = null // an additional buffer just for convenience (dim,)
//    final var hb: Array[FloatTensor] = null // buffer for hidden dimension in the ffn (hidden_dim,)
//    final var hb2: Array[FloatTensor] = null // buffer for hidden dimension in the ffn (hidden_dim,)
//    final var q: Array[FloatTensor] = null // query (dim,)
//    final var k: Array[FloatTensor] = null // key (dim,)
//    final var v: Array[FloatTensor] = null // value (dim,)
//    final var att: Array[FloatTensor] = null // buffer for scores/attention values (n_heads, seq_len)
//    final var logits: FloatTensor = null // output logits
//    // kv cache
//    final var keyCache: Array[FloatTensor] = null // (n_layer, seq_len, kv_dim)
//    final var valueCache: Array[FloatTensor] = null // (n_layer, seq_len, kv_dim)
//    /** last index in previous block */
//    var idxPrevBlock = 0
//    var latestToken = 0
//  }
//
//  def allocate(numTokens: Int, dims: Int*) = IntStream.range(0, numTokens).mapToObj((i: Int) => ArrayFloatTensor.allocate(dims)).toArray(`new`)
//
//  def rmsnorm(out: FloatTensor, x: FloatTensor, weight: FloatBuffer, size: Int, rmsNormEps: Float): Unit = {
//    // calculate sum of squares
//    var ss = x.reduce(0, size, 0f, (acc: Float, xi: Float) => acc + xi * xi)
//    ss /= size
//    ss += rmsNormEps
//    ss = (1.0 / Math.sqrt(ss)).toFloat
//    // normalize and scale
//    val finalss = ss // for the lambda
//    out.mapWithIndexInPlace(0, size, (value: Float, index: Int) => weight.get(index) * (finalss * x.getFloat(index)))
//  }
//
//  def forward(model: Llama, state: Llama.State, tokens: Array[Int], position: Int, computeLogits: Boolean): FloatTensor = {
//    // a few convenience variables
//    val config = model.configuration
//    val weights = model.weights
//    val dim = config.dim
//    val headSize = config.headSize
//    val kvDim = (config.dim * config.numberOfKeyValueHeads) / config.numberOfHeads
//    val kvMul = config.numberOfHeads / config.numberOfKeyValueHeads // integer multiplier of the kv sharing in multiquery
//    val sqrtHeadSize = Math.sqrt(headSize).toFloat
//    val nTokens = tokens.length
//    // copy the token embedding into x
//    Parallel.parallelFor(0, nTokens, (t: Int) => weights.token_embedding_table.copyTo(tokens(t) * dim, state.x(t), 0, dim))
//    // forward all the layers
//    for (l <- 0 until config.numberOfLayers) {
//      // attention rmsnorm
//      // rmsnorm(state.xb, state.x, weights.rms_att_weight[l], dim, config.rmsNormEps);
//      val curLayer = l
//      Parallel.parallelFor(0, nTokens, (t: Int) => rmsnorm(state.xb(t), state.x(t), weights.rms_att_weight(curLayer), dim, config.rmsNormEps))
//      // qkv matmuls for this position
//      weights.wq(l).matmul(nTokens, state.xb, state.q, dim, dim)
//      weights.wk(l).matmul(nTokens, state.xb, state.k, kvDim, dim)
//      weights.wv(l).matmul(nTokens, state.xb, state.v, kvDim, dim)
//      // RoPE relative positional encoding: complex-valued rotate q and k in each head
//      Parallel.parallelFor(0, nTokens, (t: Int) => {
//        var i = 0
//        while (i < dim) {
//          val head_dim = i % headSize
//          val fcr = weights.freq_cis_real.get((position + t) * (headSize / 2) + (head_dim / 2))
//          val fci = weights.freq_cis_imag.get((position + t) * (headSize / 2) + (head_dim / 2))
//          val rotn = if (i < kvDim) 2
//          else 1 // how many vectors? 2 = q & k, 1 = q only
//          for (vi <- 0 until rotn) {
//            val vec = if (vi == 0) state.q(t)
//            else state.k(t) // the vector to rotate (query or key)
//            val v0 = vec.getFloat(i)
//            val v1 = vec.getFloat(i + 1)
//            vec.setFloat(i, v0 * fcr - v1 * fci)
//            vec.setFloat(i + 1, v0 * fci + v1 * fcr)
//          }
//          i += 2
//        }
//      })
//      // save key,value at this time step (position) to our kv cache
//      //int loff = l * config.seq_len * kvDim; // kv cache layer offset for convenience
//      Parallel.parallelFor(0, nTokens, (t: Int) => {
//        state.k(t).copyTo(0, state.keyCache(curLayer), (position + t) * kvDim, kvDim)
//        state.v(t).copyTo(0, state.valueCache(curLayer), (position + t) * kvDim, kvDim)
//      })
//      // If the logits are not required, the attention and FFN of the last layer can be skipped entirely.
//      if (!computeLogits && curLayer == config.numberOfLayers - 1) {
//        state.idxPrevBlock = nTokens - 1
//        return null
//      }
//      // multihead attention. iterate over all heads
//      Parallel.parallelForLong(0, nTokens.toLong * config.numberOfHeads.toLong, (ht: Long) => {
//        val token = (ht / config.numberOfHeads).toInt
//        val h = (ht % config.numberOfHeads).toInt
//        // get the query vector for this head
//        // float* q = s.q + h * headSize;
//        val qOffset = h * headSize
//        // attention scores for this head
//        // float* att = s.att + h * config.seq_len;
//        val attOffset = h * config.contextLength
//        // iterate over all timesteps, including the current one
//        for (t <- 0 to position + token) {
//          // get the key vector for this head and at this timestep
//          // float* k = s.key_cache + loff + t * dim + h * headSize;
//          val keyCacheOffset = /* loff + */ t * kvDim + (h / kvMul) * headSize
//          // calculate the attention score as the dot product of q and k
//          var score = state.q(token).dot(qOffset, state.keyCache(curLayer), keyCacheOffset, headSize)
//          score /= sqrtHeadSize
//          // save the score to the attention buffer
//          state.att(token).setFloat(attOffset + t, score)
//        }
//        // softmax the scores to get attention weights, from 0..position inclusively
//        state.att(token).softmaxInPlace(attOffset, position + token + 1)
//        // weighted sum of the values, store back into xb
//        // float* xb = s.xb + h * headSize;
//        val xbOffset = h * headSize
//        // memset(xb, 0, headSize * sizeof(float));
//        state.xb(token).fillInPlace(xbOffset, headSize, 0f)
//        for (t <- 0 to position + token) {
//          // get the value vector for this head and at this timestep
//          // float* v = s.value_cache + loff + t * dim + h * headSize;
//          val vOffset = /* loff + */ t * kvDim + (h / kvMul) * headSize
//          // get the attention weight for this timestep
//          val a = state.att(token).getFloat(attOffset + t)
//          // accumulate the weighted value into xb
//          state.xb(token).saxpyInPlace(xbOffset, state.valueCache(curLayer), vOffset, headSize, a)
//        }
//      })
//      // final matmul to get the output of the attention
//      weights.wo(l).matmul(nTokens, state.xb, state.xb2, dim, dim)
//      // residual connection back into x
//      Parallel.parallelFor(0, nTokens, (t: Int) => {
//        state.x(t).addInPlace(state.xb2(t))
//      })
//      // ffn rmsnorm
//      Parallel.parallelFor(0, nTokens, (t: Int) => {
//        rmsnorm(state.xb(t), state.x(t), weights.rms_ffn_weight(curLayer), dim, config.rmsNormEps)
//      })
//      // Now for FFN in PyTorch we have: self.w2(F.silu(self.w1(x)) * self.w3(x))
//      // first calculate self.w1(x) and self.w3(x)
//      weights.w1(l).matmul(nTokens, state.xb, state.hb, config.hiddenDim, dim)
//      weights.w3(l).matmul(nTokens, state.xb, state.hb2, config.hiddenDim, dim)
//      // SwiGLU non-linearity
//      // silu(x)=x*σ(x), where σ(x) is the logistic sigmoid
//      Parallel.parallelFor(0, nTokens, (t: Int) => {
//        state.hb(t).mapInPlace((value: Float) => value / (1.0 + Math.exp(-value)).toFloat)
//      })
//      // elementwise multiply with w3(x)
//      Parallel.parallelFor(0, nTokens, (t: Int) => {
//        state.hb(t).multiplyInPlace(state.hb2(t))
//      })
//      // final matmul to get the output of the ffn
//      weights.w2(l).matmul(nTokens, state.hb, state.xb, dim, config.hiddenDim)
//      // residual connection
//      Parallel.parallelFor(0, nTokens, (t: Int) => {
//        state.x(t).addInPlace(state.xb(t))
//      })
//    }
//    // final rmsnorm
//    Parallel.parallelFor(0, nTokens, (t: Int) => {
//      rmsnorm(state.x(t), state.x(t), weights.rms_final_weight, dim, config.rmsNormEps)
//    })
//    // classifier into logits
//    weights.wcls.matmul(state.x(nTokens - 1), state.logits, config.vocabularySize, dim)
//    state.idxPrevBlock = nTokens - 1
//    state.logits
//  }
//
//  /**
//   * LLM generation entry point, ingest prompt tokens and generates new tokens.
//   *
//   * <p>
//   * All prompt tokens are ingested first, then inference starts, until a stop token is found.
//   * The returned tokens only include generated/inferred tokens.
//   *
//   * @param model            model to run inference (including weights, configuration, tokenizer ...)
//   * @param state            state of the model e.g. key/value caches ... this is mutated by this call
//   * @param startPosition    start prompt ingestion + inference at this position in the context e.g. useful if state was kept across calls (chained generation). 0 implies run with no previous context.
//   * @param promptTokens     prompt tokens to ingest, all the prompt tokens will be ingested, given there's enough capacity left in the context
//   * @param stopTokens       set of tokens that abort generation during inference, stop tokens do not affect prompt ingestion
//   * @param maxTokens        maximum number of tokens (can go up to {@link Configuration# contextLength context length}
//   *                         if this value is negative or greater than {@link Configuration# contextLength context length}
//   * @param sampler          {@link Sampler strategy} used to select tokens
//   * @param echo             debugging flag, prints ALL, prompt and inferred tokens, to {@link System# err stderr}
//   * @param onTokenGenerated callback, if non-null, it's called every time a token is inferred e.g. it's not called when ingesting prompt tokens
//   * @return list of generated/inferred tokens, including the stop token, if any e.g. does not include any token from the prompt
//   */
//  def generateTokens(model: Llama, state: Llama.State, startPosition: Int, promptTokens: util.List[Integer], stopTokens: util.Set[Integer], maxTokens: Int, sampler: Sampler, echo: Boolean, onTokenGenerated: IntConsumer): util.List[Integer] = {
//    val startNanos = System.nanoTime
//    var startGen = 0
//    if (maxTokens < 0 || model.configuration.contextLength < maxTokens) maxTokens = model.configuration.contextLength
//    val generatedTokens = new util.ArrayList[Integer](maxTokens)
//    var token = state.latestToken // BOS?
//    var nextToken = 0
//    var promptIndex = 0
//    var position = startPosition
//    while (position < maxTokens) {
//      if (promptIndex < promptTokens.size) {
//        val nTokens = Math.min(maxTokens - position, Math.min(promptTokens.size - promptIndex, state.batchsize))
//        val tokens = new Array[Int](nTokens)
//        for (i <- 0 until nTokens) {
//          tokens(i) = promptTokens.get(promptIndex + i)
//          if (echo) {
//            // log prompt token (different color?)
//            System.err.print(Tokenizer.replaceControlCharacters(model.tokenizer.decode(util.List.of(tokens(i)))))
//          }
//        }
//        if (echo) System.out.format("position=%d, promptIdx=%d, promptSize=%d, tokens=%s%n", position, promptIndex, promptTokens.size, util.Arrays.toString(tokens))
//        // Only compute logits on the very last batch.
//        val computeLogits = promptIndex + nTokens >= promptTokens.size
//        forward(model, state, tokens, position, computeLogits)
//        position += nTokens - 1 // -1 -> incremented later in the for loop
//        promptIndex += nTokens
//        if (promptIndex < promptTokens.size) continue //todo: continue is not supported
//        startGen = System.nanoTime
//      }
//      else forward(model, state, Array[Int](token), position, true)
//      nextToken = sampler.sampleToken(state.logits)
//      if (echo) {
//        // log inferred token
//        System.err.print(Tokenizer.replaceControlCharacters(model.tokenizer.decode(util.List.of(nextToken))))
//      }
//      generatedTokens.add(nextToken)
//      if (onTokenGenerated != null) onTokenGenerated.accept(nextToken)
//      if (stopTokens.contains(nextToken)) break //todo: break is not supported
//      state.latestToken = token = nextToken
//      position += 1
//    }
//    val elapsedNanos = System.nanoTime - startNanos
//    val promptNanos = startGen - startNanos
//    val genNanos = elapsedNanos - startGen + startNanos
//    System.err.printf("%ncontext: %d/%d prompt: %.2f tokens/s (%d) generation: %.2f tokens/s (%d)%n", startPosition + promptIndex + generatedTokens.size, model.configuration.contextLength, promptTokens.size / (promptNanos / 1_000_000_000.0), promptTokens.size, generatedTokens.size / (genNanos / 1_000_000_000.0), generatedTokens.size)
//    generatedTokens
//  }
//}
//
//final class Llama (configuration: Llama.Configuration, tokenizer: Tokenizer, weights: Llama.Weights) {
//  this.configuration = configuration
//  this.tokenizer = tokenizer
//  this.weights = weights
//  final private val configuration: Llama.Configuration = null
//  final private val tokenizer: Tokenizer = null
//  final private val weights: Llama.Weights = null
//
//  def createNewState(batchsize: Int): Llama.State = {
//    val state = new Llama.State(configuration, batchsize)
//    state.latestToken = tokenizer.getSpecialTokens.get("<|begin_of_text|>")
//    state
//  }
//}
//
///**
// * Byte Pair Encoding tokenizer.
// * <p>
// * Based on <a href="https://github.com/karpathy/minbpe">minbpe</a>, algorithmically follows along the
// * <a href="https://github.com/openai/gpt-2/blob/master/src/encoder.py">GPT 2 tokenizer</a>
// */
//object Tokenizer {
//  private def findAll(pattern: Pattern, text: String) = {
//    val allMatches = new util.ArrayList[String]
//    val matcher = pattern.matcher(text)
//    while (matcher.find) allMatches.add(matcher.group)
//    allMatches
//  }
//
//  private def merge(ids: util.List[Integer], pair: Pair[Integer, Integer], idx: Int) = {
//    val newids = new util.ArrayList[Integer]
//    var i = 0
//    while (i < ids.size) {
//      // if not at the very last position AND the pair matches, replace it
//      if (ids.get(i) == pair.first && i < ids.size - 1 && ids.get(i + 1) == pair.second) {
//        newids.add(idx)
//        i += 2
//      }
//      else {
//        newids.add(ids.get(i))
//        i += 1
//      }
//    }
//    newids
//  }
//
//  /**
//   * Returns list of utf-8 byte and a corresponding list of unicode strings.
//   * The reversible bpe codes work on unicode strings.
//   * This means you need a large # of unicode characters in your vocab if you want to avoid UNKs.
//   * When you're at something like a 10B token dataset you end up needing around 5K for decent coverage.
//   * This is a significant percentage of your normal, say, 32K bpe vocab.
//   * To avoid that, we want lookup tables between utf-8 bytes and unicode strings.
//   * And avoids mapping to whitespace/control characters the bpe code barfs on.
//   */
//  private def bytesToUnicode = {
//    val bs = new util.ArrayList[Integer]
//    IntStream.rangeClosed('!', '~').forEach(bs.add)
//    IntStream.rangeClosed('¡', '¬').forEach(bs.add)
//    IntStream.rangeClosed('®', 'ÿ').forEach(bs.add)
//    val cs = new util.ArrayList[Integer](bs)
//    var n = 0
//    for (b <- 0 until 256) {
//      if (!bs.contains(b)) {
//        bs.add(b)
//        cs.add(256 + n)
//        n += 1
//      }
//    }
//    // return dict(zip(bs, cs))
//    IntStream.range(0, bs.size).boxed.collect(Collectors.toMap(bs.get, cs.get))
//  }
//
//  val BYTE_ENCODER = bytesToUnicode
//  val BYTE_DECODER = BYTE_ENCODER.entrySet.stream.collect(Collectors.toMap(util.Map.Entry.getValue, util.Map.Entry.getKey))
//
//  def replaceControlCharacters(codePoints: Array[Int]): String = {
//    // we don't want to print control characters
//    // which distort the output (e.g. \n or much worse)
//    // https://stackoverflow.com/questions/4324790/removing-control-characters-from-a-string-in-python/19016117#19016117
//    // http://www.unicode.org/reports/tr44/#GC_Values_Table\
//    val chars = new lang.StringBuilder
//    for (cp <- codePoints) {
//      if (Character.getType(cp) == Character.CONTROL && cp != '\n') chars.append("\\u").append(HexFormat.of.toHexDigits(cp, 4)) // escape
//      else chars.appendCodePoint(cp) // this character is ok
//    }
//    chars.toString
//  }
//
//  def replaceControlCharacters(str: String): String = replaceControlCharacters(str.codePoints.toArray)
//}
//
//class Tokenizer(private val vocabulary: Vocabulary, merges: util.List[Pair[Integer, Integer]], regexPattern: String, specialTokens: util.Map[String, Integer]) {
//  this.compiledPattern = if (regexPattern != null) Pattern.compile(regexPattern)
//  else null
//  this.specialTokens = new util.HashMap[String, Integer](specialTokens)
//  this.merges = new util.HashMap[Pair[Integer, Integer], Integer]
//
//  import scala.collection.JavaConversions._
//
//  for (pair <- merges) {
//    val firstIndex = pair.first
//    val secondIndex = pair.second
//    val mergeIndex = vocabulary.getIndex(vocabulary.get(firstIndex) + vocabulary.get(secondIndex)).orElseThrow
//    this.merges.put(pair, mergeIndex)
//  }
//  final private var compiledPattern: Pattern = null
//  final private var merges: util.Map[Pair[Integer, Integer], Integer] = null
//  final private var specialTokens: util.Map[String, Integer] = null
//
//  def regexPattern: String = {
//    if (compiledPattern == null) return null
//    compiledPattern.pattern
//  }
//
//  def getSpecialTokens: util.Map[String, Integer] = specialTokens
//
//  def isSpecialToken(tokenIndex: Int): Boolean = specialTokens.containsValue(tokenIndex)
//
//  private def encodeImpl(text: String) = encode(text, util.Set.of).stream.mapToInt((i: Integer) => i).toArray
//
//  /**
//   * Unlike {@link # encodeOrdinary ( String )}, this function handles special tokens.
//   * allowed_special: can be "all"|"none"|"none_raise" or a custom set of special tokens
//   * if none_raise, then an error is raised if any special token is encountered in text
//   * this is the default tiktoken behavior right now as well
//   * any other behavior is either annoying, or a major footgun.
//   */
//  def encode(text: String, allowedSpecial: util.Set[String]): util.List[Integer] = {
//    // decode the user desire w.r.t. handling of special tokens
//    val special = allowedSpecial
//    assert(getSpecialTokens.keySet.containsAll(special))
//    if (special.isEmpty) {
//      // shortcut: if no special tokens, just use the ordinary encoding
//      return encodeOrdinary(text)
//    }
//    // otherwise, we have to be careful with potential special tokens in text
//    // we handle special tokens by splitting the text
//    // based on the occurrence of any exact match with any of the special tokens
//    // we can use re.split for this. note that surrounding the pattern with ()
//    // makes it into a capturing group, so the special tokens will be included
//    val specialPattern = special.stream.map(Pattern.quote).collect(Collectors.joining("|", "(", ")"))
//    val specialChunks = text.split(specialPattern)
//    // now all the special characters are separated from the rest of the text
//    // all chunks of text are encoded separately, then results are joined
//    val ids = new util.ArrayList[Integer]
//    for (part <- specialChunks) {
//      if (special.contains(part)) {
//        // this is a special token, encode it separately as a special case
//        ids.add(getSpecialTokens.get(part))
//      }
//      else {
//        // this is an ordinary sequence, encode it normally
//        ids.addAll(encodeOrdinary(part))
//      }
//    }
//    ids
//  }
//
//  /**
//   * Encoding that ignores any special tokens.
//   */
//  def encodeOrdinary(text: String): util.List[Integer] = {
//    // split text into chunks of text by categories defined in regex pattern
//    val textChunks = Tokenizer.findAll(compiledPattern, text)
//    // all chunks of text are encoded separately, then results are joined
//    val ids = new util.ArrayList[Integer]
//    import scala.collection.JavaConversions._
//    for (chunk <- textChunks) {
//      val chunkIds = encodeChunk(chunk)
//      ids.addAll(chunkIds)
//    }
//    ids
//  }
//
//  private def getStats(ids: util.List[Integer]) = {
//    val map = new util.HashMap[Pair[Integer, Integer], Integer]
//    var i = 0
//    while (i + 1 < ids.size) {
//      val key = new Pair[Integer, Integer](ids.get(i), ids.get(i + 1))
//      map.put(key, map.getOrDefault(key, 0) + 1)
//      i += 1
//    }
//    map
//  }
//
//  private def encodeChunk(chunk: String) = {
//    // return the token ids
//    // let's begin. first, convert all bytes to integers in range 0..255
//    var ids = new util.ArrayList[Integer]
//    for (b <- chunk.toCharArray) {
//      val tokenIndex = this.vocabulary.getIndex(String.valueOf(b.toChar)).orElseThrow
//      ids.add(tokenIndex)
//    }
//    while (ids.size >= 2) {
//      // find the pair with the lowest merge index
//      val stats = getStats(ids)
//      val pair = stats.keySet.stream.min(Comparator.comparingInt((key: Pair[Integer, Integer]) => this.merges.getOrDefault(key, Integer.MAX_VALUE))).orElseThrow
//      // subtle: if there are no more merges available, the key will
//      // result in an inf for every single pair, and the min will be
//      // just the first pair in the list, arbitrarily
//      // we can detect this terminating case by a membership check
//      if (!this.merges.containsKey(pair)) {
//        break //todo: break is not supported
//        // nothing else can be merged anymore
//      }
//      // otherwise let's merge the best pair (lowest merge index)
//      val idx = this.merges.get(pair)
//      ids = Tokenizer.merge(ids, pair, idx)
//    }
//    ids
//  }
//
//  def decodeImpl(tokens: util.List[Integer]): String = {
//    val sb = new lang.StringBuilder
//    import scala.collection.JavaConversions._
//    for (token <- tokens) {
//      val tokenString = vocabulary.get(token)
//      sb.append(tokenString)
//    }
//    sb.toString
//  }
//
//  def encode(text: String): Array[Int] = {
//    val sb = new lang.StringBuilder
//    val bytes = text.getBytes(StandardCharsets.UTF_8)
//    for (b <- bytes) {
//      sb.appendCodePoint(Tokenizer.BYTE_ENCODER.get(Byte.toUnsignedInt(b)))
//    }
//    encodeImpl(sb.toString)
//  }
//
//  def encodeAsList(text: String): util.List[Integer] = util.Arrays.stream(encode(text)).boxed.toList
//
//  def decode(tokens: util.List[Integer]): String = {
//    val decoded = decodeImpl(tokens)
//    val decodedBytesAsInts = decoded.codePoints.map(Tokenizer.BYTE_DECODER.get).toArray
//    val rawBytes = new Array[Byte](decodedBytesAsInts.length)
//    for (i <- 0 until decoded.length) {
//      rawBytes(i) = decodedBytesAsInts(i).toByte
//    }
//    new String(rawBytes, StandardCharsets.UTF_8)
//  }
//}
//
//object Parallel {
//  def parallelFor(startInclusive: Int, endExclusive: Int, action: IntConsumer): Unit = {
//    if (startInclusive == 0 && endExclusive == 1) {
//      action.accept(0)
//      return
//    }
//    IntStream.range(startInclusive, endExclusive).parallel.forEach(action)
//  }
//
//  def parallelForLong(startInclusive: Long, endExclusive: Long, action: LongConsumer): Unit = {
//    if (startInclusive == 0 && endExclusive == 1) {
//      action.accept(0)
//      return
//    }
//    LongStream.range(startInclusive, endExclusive).parallel.forEach(action)
//  }
//}
//
//final class Pair[First, Second] (first: First, second: Second) {
//  this.first = first
//  this.second = second
//  final private val first: First = null
//  final private val second: Second = null
//}
//
//final class GGMLTensorEntry (mappedFile: MemorySegment, name: String, ggmlType: GGMLType, shape: Array[Int], memorySegment: MemorySegment) {
//  this.mappedFile = mappedFile
//  this.name = name
//  this.ggmlType = ggmlType
//  this.shape = shape
//  this.memorySegment = memorySegment
//  final private val mappedFile: MemorySegment = null
//  final private val name: String = null
//  final private val ggmlType: GGMLType = null
//  final private val shape: Array[Int] = null
//  final private val memorySegment: MemorySegment = null
//}
//
//object GGMLType extends util.Enumeration {
//  type GGMLType = Value
//  val F32, F16, Q4_0, Q4_1, UNSUPPORTED_Q4_2, // support has been removed
//  UNSUPPORTED_Q4_3, // support has been removed
//  Q5_0, Q5_1, Q8_0, Q8_1, // k-quantizations
//  Q2_K, Q3_K, Q4_K, Q5_K, Q6_K, Q8_K, IQ2_XXS, IQ2_XS, IQ3_XXS, IQ1_S, IQ4_NL, IQ3_S, IQ2_S, IQ4_XS, I8, I16, I32, I64, F64, IQ1_M, BF16, Q4_0_4_4, Q4_0_4_8, Q4_0_8_8, TQ1_0, TQ2_0 = Value
//  val BFLOAT16_BYTES = 2
//  val FLOAT16_BYTES = 2
//  private val VALUES = valuesprivate
//  val typeSize = 0
//  private val blockSize = 0d ef getTypeSize: Int
//  =
//  {
//    return typeSize
//  }
//
//  def getBlockSize: Int = blockSize
//
//  def fromId(id: Int): GGMLType = VALUES(id)
//
//  def this(typeSize: Int) {
//    this(typeSize, 1)
//  }
//
//  def byteSizeFor(numberOfElements: Int): Long = {
//    val t = numberOfElements * getTypeSize.toLong
//    assert(t % getBlockSize == 0)
//    Math.toIntExact(t / getBlockSize)
//  }
//
//  val QK_K = 256 // or 64?
//
//  def this(typeSize: Int, blockSize: Int)
//
//  private def isPowerOf2(n: Int) = n > 0 && (n & (n - 1)) == 0
//}
//
///**
// * Over-simplified, shapeless, float tensor.
// * <p>
// * Not a strict tensor, but rather just a sequence of floats, not required to be backed by memory
// * e.g. can represent a sequence of quantized floats.
// */
//object FloatTensor {
//  val VECTOR_BIT_SIZE = Integer.getInteger("llama.VectorBitSize", VectorShape.preferredShape.vectorBitSize)
//  val USE_VECTOR_API = VECTOR_BIT_SIZE != 0
//  // The use of Unsafe in this file is a temporary workaround to support native-image.
//  // static final ValueLayout.OfFloat JAVA_FLOAT_LE = ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN);
//  // static final ValueLayout.OfShort JAVA_SHORT_LE = ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN);
//  var UNSAFE: Unsafe = null
//
//  def readShort(memorySegment: MemorySegment, offset: Long) = {
//    // The MemorySegment.get* methods should be used instead.
//    UNSAFE.getShort(memorySegment.address + offset)
//  }
//
//  def readByte(memorySegment: MemorySegment, offset: Long) = {
//    // The MemorySegment.get* methods should be used instead.
//    UNSAFE.getByte(memorySegment.address + offset)
//  }
//
//  // Preferred vector size for the fast multiplication routines.
//  // (Apple Silicon) NEON only supports up-to 128bit vectors.
//  var F_SPECIES: VectorSpecies[Float] = null
//  var I_SPECIES: VectorSpecies[Integer] = null
//  var S_SPECIES_HALF: VectorSpecies[Short] = null
//
//  def numberOfElements(dimensions: Int*): Int = {
//    assert(util.Arrays.stream(dimensions).allMatch((i: Int) => i > 0))
//    util.Arrays.stream(dimensions).reduce(Math.multiplyExact).orElseThrow
//  }
//
//  def scalarDot(thiz: FloatTensor, thisOffset: Int, that: FloatTensor, thatOffset: Int, size: Int) = {
//    var result = 0f
//    for (j <- 0 until size) {
//      result += thiz.getFloat(thisOffset + j) * that.getFloat(thatOffset + j)
//    }
//    result
//  }
//
//  @FunctionalInterface  trait AggregateFunction {
//    def apply(acc: Float, value: Float): Float
//  }
//
//  @FunctionalInterface  trait MapFunction {
//    def apply(value: Float): Float
//  }
//
//  @FunctionalInterface  trait MapWithIndexFunction {
//    def apply(value: Float, index: Int): Float
//  }
//
//  try try {
//    val f = classOf[Unsafe].getDeclaredField("theUnsafe")
//    f.setAccessible(true)
//    UNSAFE = f.get(null).asInstanceOf[Unsafe]
//  } catch {
//    case e@(_: NoSuchFieldException | _: IllegalAccessException) =>
//      throw new RuntimeException(e)
//  }
//  if (USE_VECTOR_API) {
//    F_SPECIES = VectorShape.forBitSize(VECTOR_BIT_SIZE).withLanes(classOf[Float])
//    I_SPECIES = F_SPECIES.withLanes(classOf[Int])
//    S_SPECIES_HALF = VectorShape.forBitSize(F_SPECIES.vectorBitSize / 2).withLanes(classOf[Short])
//    assert(F_SPECIES.length == S_SPECIES_HALF.length)
//  }
//  else {
//    F_SPECIES = null
//    I_SPECIES = null
//    S_SPECIES_HALF = null
//  }
//}
//
//abstract class FloatTensor {
//  def size: Int
//
//  def getFloat(index: Int): Float
//
//  def setFloat(index: Int, value: Float): Unit
//
//  def getFloatVector(species: VectorSpecies[Float], offset: Int): FloatVector
//
//  def `type`: GGMLType
//
//  def dot(thisOffset: Int, that: FloatTensor, thatOffset: Int, size: Int) = FloatTensor.scalarDot(this, thisOffset, that, thatOffset, size)
//
//  def matmul(that: FloatTensor, out: FloatTensor, dim0: Int, dim1: Int): Unit = {
//    Parallel.parallelFor(0, dim0, (i: Int) => out.setFloat(i, dot(i * dim1, that, 0, dim1)))
//  }
//
//  def matmul(context: Int, that: Array[FloatTensor], out: Array[FloatTensor], dim0: Int, dim1: Int): Unit = {
//    if (that.length != out.length) throw new IllegalArgumentException(String.format("that.len=%d, out.len=%d", that.length, out.length))
//    Parallel.parallelForLong(0, dim0 * context, (ti: Long) => {
//      val idxArr = (ti / dim0).toInt
//      val i = (ti % dim0).toInt
//      out(idxArr).setFloat(i, dot(i * dim1, that(idxArr), 0, dim1))
//    })
//  }
//
//  def reduce(thisOffset: Int, size: Int, seed: Float, reduce: FloatTensor.AggregateFunction) = {
//    var result = seed
//    for (i <- 0 until size) {
//      result = reduce.apply(result, getFloat(thisOffset + i))
//    }
//    result
//  }
//
//  def sum(thisOffset: Int, size: Int) = reduce(thisOffset, size, 0f, Float.sum)
//
//  def max(thisOffset: Int, size: Int) = reduce(thisOffset, size, Float.NEGATIVE_INFINITY, Float.max)
//
//  def copyTo(thisOffset: Int, that: FloatTensor, thatOffset: Int, size: Int): Unit = {
//    that.mapWithIndexInPlace(thatOffset, size, (value: Float, index: Int) => this.getFloat(index - thatOffset + thisOffset))
//  }
//
//  def argmax(thisOffset: Int, size: Int) = {
//    assert(size > 0)
//    var maxIndex = thisOffset
//    var maxValue = this.getFloat(maxIndex)
//    val endIndex = thisOffset + size
//    for (i <- thisOffset until endIndex) {
//      val f = this.getFloat(i)
//      if (f > maxValue) {
//        maxValue = f
//        maxIndex = i
//      }
//    }
//    maxIndex
//  }
//
//  def argmax = argmax(0, size)
//
//  def mapInPlace(thisOffset: Int, size: Int, mapFunction: FloatTensor.MapFunction) = {
//    val endIndex = thisOffset + size
//    for (i <- thisOffset until endIndex) {
//      setFloat(i, mapFunction.apply(getFloat(i)))
//    }
//    this
//  }
//
//  def mapInPlace(mapFunction: FloatTensor.MapFunction) = mapInPlace(0, size, mapFunction)
//
//  def mapWithIndexInPlace(thisOffset: Int, size: Int, mapWithIndexFunction: FloatTensor.MapWithIndexFunction) = {
//    val endOffset = thisOffset + size
//    for (i <- thisOffset until endOffset) {
//      setFloat(i, mapWithIndexFunction.apply(getFloat(i), i))
//    }
//    this
//  }
//
//  def addInPlace(thisOffset: Int, that: FloatTensor, thatOffset: Int, size: Int) = mapWithIndexInPlace(thisOffset, size, (value: Float, index: Int) => value + that.getFloat(index - thisOffset + thatOffset))
//
//  def addInPlace(that: FloatTensor) = addInPlace(0, that, 0, size)
//
//  def multiplyInPlace(thisOffset: Int, that: FloatTensor, thatOffset: Int, size: Int) = mapWithIndexInPlace(thisOffset, size, (value: Float, index: Int) => value * that.getFloat(index - thisOffset + thatOffset))
//
//  def multiplyInPlace(that: FloatTensor) = multiplyInPlace(0, that, 0, size)
//
//  def divideInPlace(thisOffset: Int, size: Int, value: Float) = mapInPlace(thisOffset, size, (f: Float) => f / value)
//
//  def fillInPlace(thisOffset: Int, size: Int, value: Float) = mapInPlace(thisOffset, size, (unused: Float) => value)
//
//  def softmaxInPlace(thisOffset: Int, size: Int) = {
//    // find max value (for numerical stability)
//    val maxVal = max(thisOffset, size)
//    // exp and sum
//    mapInPlace(thisOffset, size, (f: Float) => Math.exp(f - maxVal).toFloat)
//    val sum = sum(thisOffset, size)
//    // normalize
//    divideInPlace(thisOffset, size, sum)
//  }
//
//  def saxpyInPlace(thisOffset: Int, that: FloatTensor, thatOffset: Int, size: Int, a: Float) = {
//    // this[thatOffset ... thatOffset + size) = a * that[thatOffset ... thatOffset + size) + this[thisOffset ... thisOffset + size)
//    for (i <- 0 until size) {
//      setFloat(thisOffset + i, a * that.getFloat(thatOffset + i) + this.getFloat(thisOffset + i))
//    }
//    this
//  }
//}
//
///**
// * {@link FloatTensor} quantized in the {@link GGMLType# Q4_0} format.
// * <p>
// * This tensor implementation is not compatible with {@link FloatTensor}, but
// * {@link # dot ( int, FloatTensor, int, int)} has a vectorized implementation that is used when
// * the second argument implements {@link FloatTensor}.
// */
//object Q4_0FloatTensor {
//  private def vectorDot(thiz: Q4_0FloatTensor, thisOffset: Int, that: ArrayFloatTensor, thatOffset: Int, size: Int) = {
//    var result = 0f
//    var j = 0
//    // Align thisOffset + j to type().getBlockSize().
//    assert(Integer.bitCount(GGMLType.Q4_0.getBlockSize) == 1, "power of 2")
//    val alignmentBound = Math.min(size, -thisOffset & (GGMLType.Q4_0.getBlockSize - 1))
//    if (alignmentBound > 0) {
//      result += FloatTensor.scalarDot(thiz, thisOffset, that, thatOffset, alignmentBound)
//      j += alignmentBound
//    }
//    assert((thisOffset + j) % GGMLType.Q4_0.getBlockSize == 0)
//    var `val` = FloatVector.zero(F_SPECIES)
//    var blockOffset = (thisOffset + j) / GGMLType.Q4_0.getBlockSize * GGMLType.Q4_0.getTypeSize
//    val upperBound = size / GGMLType.Q4_0.getBlockSize * GGMLType.Q4_0.getBlockSize
//    while (j < upperBound) {
//      val wScaleValue = Float.float16ToFloat(readShort(thiz.memorySegment, blockOffset))
//      val wScale = FloatVector.broadcast(F_SPECIES, wScaleValue)
//      val wBytes = ByteVector.fromMemorySegment(ByteVector.SPECIES_128, thiz.memorySegment, blockOffset + GGMLType.FLOAT16_BYTES, ByteOrder.LITTLE_ENDIAN)
//      val loBytes = wBytes.and(0xF.toByte).sub(8.toByte)
//      val hiBytes = wBytes.lanewise(VectorOperators.LSHR, 4).sub(8.toByte)
//      F_SPECIES.vectorBitSize match {
//        case 512 =>
//          val sum0 = that.getFloatVector(F_SPECIES, thatOffset + j + 0 * F_SPECIES.length).mul(loBytes.castShape(F_SPECIES, 0))
//          val sum2 = that.getFloatVector(F_SPECIES, thatOffset + j + 1 * F_SPECIES.length).mul(hiBytes.castShape(F_SPECIES, 0))
//          `val` = sum0.add(sum2).fma(wScale, `val`)
//        case 256 =>
//          val sum0 = that.getFloatVector(F_SPECIES, thatOffset + j + 0 * F_SPECIES.length).mul(loBytes.castShape(F_SPECIES, 0))
//          val sum1 = that.getFloatVector(F_SPECIES, thatOffset + j + 1 * F_SPECIES.length).mul(loBytes.castShape(F_SPECIES, 1))
//          val sum2 = that.getFloatVector(F_SPECIES, thatOffset + j + 2 * F_SPECIES.length).mul(hiBytes.castShape(F_SPECIES, 0))
//          val sum3 = that.getFloatVector(F_SPECIES, thatOffset + j + 3 * F_SPECIES.length).mul(hiBytes.castShape(F_SPECIES, 1))
//          `val` = sum0.add(sum1).add(sum2).add(sum3).fma(wScale, `val`)
//        case 128 =>
//          // This loop cannot be unrolled, why?
//          for (i <- 0 until 2) {
//            val tmp = if (i == 0) loBytes
//            else hiBytes
//            val sum0 = that.getFloatVector(F_SPECIES, thatOffset + j + (i * 4 + 0) * F_SPECIES.length).mul(tmp.castShape(F_SPECIES, 0))
//            val sum1 = that.getFloatVector(F_SPECIES, thatOffset + j + (i * 4 + 1) * F_SPECIES.length).mul(tmp.castShape(F_SPECIES, 1))
//            val sum2 = that.getFloatVector(F_SPECIES, thatOffset + j + (i * 4 + 2) * F_SPECIES.length).mul(tmp.castShape(F_SPECIES, 2))
//            val sum3 = that.getFloatVector(F_SPECIES, thatOffset + j + (i * 4 + 3) * F_SPECIES.length).mul(tmp.castShape(F_SPECIES, 3))
//            `val` = sum0.add(sum1).add(sum2).add(sum3).fma(wScale, `val`)
//          }
//        case _ => throw new UnsupportedOperationException(F_SPECIES.toString)
//      }
//      j += GGMLType.Q4_0.getBlockSize
//      blockOffset += GGMLType.Q4_0.getTypeSize
//    }
//    result += `val`.reduceLanes(VectorOperators.ADD)
//    // Remaining entries.
//    if (j < size) result += FloatTensor.scalarDot(thiz, thisOffset + j, that, thatOffset + j, size - j)
//    result
//  }
//}
//
//final class Q4_0FloatTensor(val size: Int, val memorySegment: MemorySegment) extends FloatTensor {
//  override def size = size
//
//  override def setFloat(index: Int, value: Float): Unit = {
//    throw new UnsupportedOperationException("setFloat")
//  }
//
//  override def getFloatVector(species: VectorSpecies[Float], index: Int): FloatVector = throw new UnsupportedOperationException("getFloatVector")
//
//  override def `type`: GGMLType = GGMLType.Q4_0
//
//  override def getFloat(index: Int): Float = {
//    assert(0 <= index && index < size)
//    val blockIndex = index / GGMLType.Q4_0.getBlockSize
//    val blockOffset = blockIndex * GGMLType.Q4_0.getTypeSize
//    val scale = Float.float16ToFloat(readShort(memorySegment, blockOffset))
//    var quant = 0
//    val modIndex = index % GGMLType.Q4_0.getBlockSize
//    if (modIndex < GGMLType.Q4_0.getBlockSize / 2) quant = (readByte(memorySegment, blockOffset + GGMLType.FLOAT16_BYTES + modIndex) & 0x0F).toByte
//    else quant = ((readByte(memorySegment, blockOffset + GGMLType.FLOAT16_BYTES + modIndex - GGMLType.Q4_0.getBlockSize / 2) >>> 4) & 0x0F).toByte
//    quant -= 8
//    quant * scale
//  }
//
//  override def dot(thisOffset: Int, that: FloatTensor, thatOffset: Int, size: Int): Float = if (FloatTensor.USE_VECTOR_API) Q4_0FloatTensor.vectorDot(this, thisOffset, that.asInstanceOf[ArrayFloatTensor], thatOffset, size)
//  else FloatTensor.scalarDot(this, thisOffset, that, thatOffset, size)
//}
//
//object Q8_0FloatTensor {
//  val JAVA_SHORT_LE: ValueLayout.OfShort = ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN)
//
//  private def vectorDot(thiz: Q8_0FloatTensor, thisOffset: Int, that: ArrayFloatTensor, thatOffset: Int, size: Int) = {
//    var result = 0f
//    var j = 0
//    // Align thisOffset + startIndex to type().getBlockSize().
//    assert(Integer.bitCount(GGMLType.Q8_0.getBlockSize) == 1, "power of 2")
//    val alignmentBound = Math.min(size, -thisOffset & (GGMLType.Q8_0.getBlockSize - 1))
//    if (alignmentBound > 0) {
//      result += FloatTensor.scalarDot(thiz, thisOffset, that, thatOffset, alignmentBound)
//      j += alignmentBound
//    }
//    assert((thisOffset + j) % GGMLType.Q8_0.getBlockSize == 0)
//    var `val` = FloatVector.zero(F_SPECIES)
//    var blockOffset = (thisOffset + j) / GGMLType.Q8_0.getBlockSize * GGMLType.Q8_0.getTypeSize
//    val upperBound = size / GGMLType.Q8_0.getBlockSize * GGMLType.Q8_0.getBlockSize
//    while (j < upperBound) {
//      val wScaleValue = Float.float16ToFloat(readShort(thiz.memorySegment, blockOffset))
//      val wScale = FloatVector.broadcast(F_SPECIES, wScaleValue)
//      F_SPECIES.vectorBitSize match {
//        case 512 =>
//          val wBytes = ByteVector.fromMemorySegment(ByteVector.SPECIES_256, thiz.memorySegment, blockOffset + GGMLType.FLOAT16_BYTES, ByteOrder.LITTLE_ENDIAN)
//          val sum0 = that.getFloatVector(F_SPECIES, thatOffset + j + 0 * F_SPECIES.length).mul(wBytes.castShape(F_SPECIES, 0))
//          val sum1 = that.getFloatVector(F_SPECIES, thatOffset + j + 1 * F_SPECIES.length).mul(wBytes.castShape(F_SPECIES, 1))
//          `val` = sum0.add(sum1).fma(wScale, `val`)
//        case 256 =>
//          val wBytes = ByteVector.fromMemorySegment(ByteVector.SPECIES_256, thiz.memorySegment, blockOffset + GGMLType.FLOAT16_BYTES, ByteOrder.LITTLE_ENDIAN)
//          val sum0 = that.getFloatVector(F_SPECIES, thatOffset + j + 0 * F_SPECIES.length).mul(wBytes.castShape(F_SPECIES, 0))
//          val sum1 = that.getFloatVector(F_SPECIES, thatOffset + j + 1 * F_SPECIES.length).mul(wBytes.castShape(F_SPECIES, 1))
//          val sum2 = that.getFloatVector(F_SPECIES, thatOffset + j + 2 * F_SPECIES.length).mul(wBytes.castShape(F_SPECIES, 2))
//          val sum3 = that.getFloatVector(F_SPECIES, thatOffset + j + 3 * F_SPECIES.length).mul(wBytes.castShape(F_SPECIES, 3))
//          `val` = sum0.add(sum1).add(sum2).add(sum3).fma(wScale, `val`)
//        case 128 =>
//          // This loop cannot be unrolled, why?
//          for (i <- 0 until 2) {
//            val wBytes = ByteVector.fromMemorySegment(ByteVector.SPECIES_128, thiz.memorySegment, blockOffset + GGMLType.FLOAT16_BYTES + i * ByteVector.SPECIES_128.vectorByteSize, ByteOrder.LITTLE_ENDIAN)
//            val sum0 = that.getFloatVector(F_SPECIES, thatOffset + j + i * 16 + 0 * F_SPECIES.length).mul(wBytes.castShape(F_SPECIES, 0))
//            val sum1 = that.getFloatVector(F_SPECIES, thatOffset + j + i * 16 + 1 * F_SPECIES.length).mul(wBytes.castShape(F_SPECIES, 1))
//            val sum2 = that.getFloatVector(F_SPECIES, thatOffset + j + i * 16 + 2 * F_SPECIES.length).mul(wBytes.castShape(F_SPECIES, 2))
//            val sum3 = that.getFloatVector(F_SPECIES, thatOffset + j + i * 16 + 3 * F_SPECIES.length).mul(wBytes.castShape(F_SPECIES, 3))
//            `val` = sum0.add(sum1).add(sum2).add(sum3).fma(wScale, `val`)
//          }
//        case _ => throw new UnsupportedOperationException(F_SPECIES.toString)
//      }
//      j += GGMLType.Q8_0.getBlockSize
//      blockOffset += GGMLType.Q8_0.getTypeSize
//    }
//    result += `val`.reduceLanes(VectorOperators.ADD)
//    // Remaining entries.
//    if (j < size) result += FloatTensor.scalarDot(thiz, thisOffset + j, that, thatOffset + j, size - j)
//    result
//  }
//}
//
//final class Q8_0FloatTensor(val size: Int, val memorySegment: MemorySegment) extends FloatTensor {
//  override def size = size
//
//  override def setFloat(index: Int, value: Float): Unit = {
//    throw new UnsupportedOperationException("setFloat")
//  }
//
//  override def getFloatVector(species: VectorSpecies[Float], index: Int): FloatVector = throw new UnsupportedOperationException("getFloatVector")
//
//  override def `type`: GGMLType = GGMLType.Q8_0
//
//  override def getFloat(index: Int): Float = {
//    assert(0 <= index && index < size)
//    val blockIndex = index / GGMLType.Q8_0.getBlockSize
//    val withinBlockIndex = index % GGMLType.Q8_0.getBlockSize
//    val blockOffset = blockIndex * GGMLType.Q8_0.getTypeSize
//    val quant = readByte(memorySegment, blockOffset + GGMLType.FLOAT16_BYTES + withinBlockIndex)
//    val scale = Float.float16ToFloat(readShort(memorySegment, blockOffset))
//    quant * scale
//  }
//
//  override def dot(thisOffset: Int, that: FloatTensor, thatOffset: Int, size: Int): Float = if (FloatTensor.USE_VECTOR_API) Q8_0FloatTensor.vectorDot(this, thisOffset, that.asInstanceOf[ArrayFloatTensor], thatOffset, size)
//  else FloatTensor.scalarDot(this, thisOffset, that, thatOffset, size)
//}
//
//object BF16FloatTensor {
//  private def vectorDot(thiz: BF16FloatTensor, thisOffset: Int, that: ArrayFloatTensor, thatOffset: Int, size: Int) = {
//    assert(S_SPECIES_HALF.length == F_SPECIES.length)
//    var `val` = FloatVector.zero(F_SPECIES)
//    val upperBound = F_SPECIES.loopBound(size)
//    var i = 0
//    while (i < upperBound) {
//      val thatVector = that.getFloatVector(F_SPECIES, thatOffset + i)
//      val bfloat16 = ShortVector.fromMemorySegment(S_SPECIES_HALF, thiz.memorySegment, (thisOffset + i) * GGMLType.BFLOAT16_BYTES.toLong, ByteOrder.LITTLE_ENDIAN)
//      // BFloat16 to Float32 Conversion:
//      //
//      // ┌─[15]─┬─[14]───····───[7]─┬─[6]────····────[0]─┐
//      // │ Sign │ Exponent (8 bits) │ Mantissa (7 bits)  │ BFloat16 Layout (16 bits)
//      // └──────┴───────────────────┴────────────────────┘
//      //    │             │                    │
//      //    ▼             ▼                    ▼
//      // ┌─[31]─┬─[30]───···───[23]─┬─[22]────···────[0]─┐
//      // │ Sign │ Exponent (8 bits) │ Mantissa (23 bits) │ Float32 Layout (32 bits)
//      // └──────┴───────────────────┴────────────────────┘
//      val thizVector = bfloat16.castShape(I_SPECIES, 0) // (int) vi.lanewise(VectorOperators.LSHL, 16)// vi <<= 16.reinterpretAsFloats// Float.intBitsToFloat(vi)
//      `val` = thizVector.fma(thatVector, `val`)
//      i += F_SPECIES.length
//    }
//    var result = `val`.reduceLanes(VectorOperators.ADD)
//    // Remaining entries.
//    if (upperBound < size) result += scalarDot(thiz, thisOffset + upperBound, that, thatOffset + upperBound, size - upperBound)
//    result
//  }
//}
//
//final class BF16FloatTensor(val size: Int, val memorySegment: MemorySegment) extends FloatTensor {
//  override def size = size
//
//  override def setFloat(index: Int, value: Float): Unit = {
//    throw new UnsupportedOperationException("setFloat")
//  }
//
//  override def getFloatVector(species: VectorSpecies[Float], index: Int): FloatVector = throw new UnsupportedOperationException("getFloatVector")
//
//  override def `type`: GGMLType = GGMLType.BF16
//
//  override def getFloat(index: Int): Float = {
//    assert(0 <= index && index < size)
//    bfloat16ToFloat(readShort(memorySegment, index * GGMLType.BFLOAT16_BYTES))
//  }
//
//  private def bfloat16ToFloat(bfloat16: Short) = Float.intBitsToFloat(bfloat16 << 16)
//
//  override def dot(thisOffset: Int, that: FloatTensor, thatOffset: Int, size: Int): Float = if (FloatTensor.USE_VECTOR_API) BF16FloatTensor.vectorDot(this, thisOffset, that.asInstanceOf[ArrayFloatTensor], thatOffset, size)
//  else FloatTensor.scalarDot(this, thisOffset, that, thatOffset, size)
//}
//
//object F16FloatTensor {
//  private def vectorDot(thiz: F16FloatTensor, thisOffset: Int, that: ArrayFloatTensor, thatOffset: Int, size: Int) = {
//    assert(S_SPECIES_HALF.length == F_SPECIES.length)
//    var `val` = FloatVector.zero(F_SPECIES)
//    val upperBound = F_SPECIES.loopBound(size)
//    var i = 0
//    while (i < upperBound) {
//      val thatVector = that.getFloatVector(F_SPECIES, thatOffset + i)
//      val bits16 = ShortVector.fromMemorySegment(S_SPECIES_HALF, thiz.memorySegment, (thisOffset + i) * GGMLType.FLOAT16_BYTES.toLong, ByteOrder.LITTLE_ENDIAN)
//      var bits32 = bits16.castShape(I_SPECIES, 0).reinterpretAsInts // (int) bits16
//      // Does not support infinities nor NaNs, preserves sign, emulate DAZ (denormals-are-zero).
//      // Expects well-formed float16 values only (e.g. model weights).
//      // Fast Float16 to Float32 Conversion:
//      //
//      // ┌─[15]─┬─[14]───···───[10]─┬─[9]────····────[0]─┐
//      // │ Sign │ Exponent (5 bits) │ Mantissa (10 bits) │ Float16 Layout (16 bits)
//      // └──────┴───────────────────┴────────────────────┘
//      //    │             │                    │
//      //    ▼             ▼                    ▼
//      // ┌─[31]─┬─[30]───···───[23]─┬─[22]────···────[0]─┐
//      // │ Sign │ Exponent (8 bits) │ Mantissa (23 bits) │ Float32 Layout (32 bits)
//      // └──────┴───────────────────┴────────────────────┘
//      //
//      // Shifts and adjustments:
//      // - Sign:       float16[15] -> float32[31] (shift 16 bits up)
//      // - Exponent:   float16[10-14] -> float32[23-30] (+ bias adjustment)
//      // - Mantissa:   float16[0-9] -> float32[13-22] (shift 13 bits up)
//      //
//      // exp = bits32 & 0x7C00
//      // zeroExponentMask = exp == 0 ? 0 : ~0
//      val zeroExponentMask = bits32.and(0x7C00).neg.lanewise(VectorOperators.ASHR, 31) // = (-exp) >> 31
//      bits32 = bits32.and(0x8000).lanewise(VectorOperators.LSHL, 16) // sign.or(// exponent and mantissa combined
//      bits32.and(0x7FFF).add(0x1C000).lanewise(VectorOperators.LSHL, 13).and(zeroExponentMask) // -0, +0 and DAZ (denormals-are-zero))
//      val thizVector = bits32.reinterpretAsFloats // Float.intBitsToFloat(vi)
//      `val` = thizVector.fma(thatVector, `val`)
//      i += F_SPECIES.length
//    }
//    var result = `val`.reduceLanes(VectorOperators.ADD)
//    // Remaining entries.
//    if (upperBound < size) result += scalarDot(thiz, thisOffset + upperBound, that, thatOffset + upperBound, size - upperBound)
//    result
//  }
//}
//
//final class F16FloatTensor(val size: Int, val memorySegment: MemorySegment) extends FloatTensor {
//  override def size = size
//
//  override def setFloat(index: Int, value: Float): Unit = {
//    throw new UnsupportedOperationException("setFloat")
//  }
//
//  override def getFloatVector(species: VectorSpecies[Float], index: Int): FloatVector = throw new UnsupportedOperationException("getFloatVector")
//
//  override def `type`: GGMLType = GGMLType.F16
//
//  override def getFloat(index: Int): Float = {
//    assert(0 <= index && index < size)
//    Float.float16ToFloat(readShort(memorySegment, index * GGMLType.FLOAT16_BYTES))
//  }
//
//  override def dot(thisOffset: Int, that: FloatTensor, thatOffset: Int, size: Int): Float = if (FloatTensor.USE_VECTOR_API) F16FloatTensor.vectorDot(this, thisOffset, that.asInstanceOf[ArrayFloatTensor], thatOffset, size)
//  else FloatTensor.scalarDot(this, thisOffset, that, thatOffset, size)
//}
//
//object ArrayFloatTensor {
//  def allocate(dims: Int*): FloatTensor = {
//    val numberOfElements = FloatTensor.numberOfElements(dims)
//    new ArrayFloatTensor(new Array[Float](numberOfElements))
//  }
//}
//
//final class ArrayFloatTensor (val values: Array[Float]) extends FloatTensor {
//  override def size: Int = values.length
//
//  override def getFloat(index: Int): Float = values(index)
//
//  override def setFloat(index: Int, value: Float): Unit = {
//    values(index) = value
//  }
//
//  override def `type`: GGMLType = GGMLType.F32
//
//  override def fillInPlace(thisOffset: Int, size: Int, value: Float): FloatTensor = {
//    util.Arrays.fill(values, thisOffset, thisOffset + size, value)
//    this
//  }
//
//  override def getFloatVector(species: VectorSpecies[Float], index: Int): FloatVector = {
//    if (!USE_VECTOR_API) throw new UnsupportedOperationException
//    FloatVector.fromArray(species, values, index)
//  }
//}
//
//object RoPE {
//  def precomputeFreqsCis(contextLength: Int, headSize: Int, theta: Double, ropeScaling: Boolean, scaleFactor: Float, loFreqFactor: Float, hiFreqFactor: Float, oldContextLength: Float): Pair[Array[Float], Array[Float]] = {
//    assert(headSize % 2 == 0)
//    val cr = new Array[Float](contextLength * (headSize / 2))
//    val ci = new Array[Float](contextLength * (headSize / 2))
//    var n = 0
//    for (pos <- 0 until contextLength) {
//      var i = 0
//      while (i < headSize) {
//        var freq = (1.0 / Math.pow(theta, i / headSize.toDouble)).toFloat
//        if (ropeScaling) {
//          // Llama 3.1 scaling
//          val loFreqWavelen = oldContextLength / loFreqFactor
//          val hiFreqWavelen = oldContextLength / hiFreqFactor
//          val wavelen = (2.0 * Math.PI / freq).toFloat
//          if (wavelen < hiFreqWavelen) freq = freq
//          else if (wavelen > loFreqWavelen) freq = freq / scaleFactor
//          else {
//            val smooth = (oldContextLength / wavelen - loFreqFactor) / (hiFreqFactor - loFreqFactor)
//            freq = (1.0f - smooth) * freq / scaleFactor + smooth * freq
//          }
//        }
//        val `val` = pos * freq
//        cr(n) = Math.cos(`val`).toFloat
//        ci(n) = Math.sin(`val`).toFloat
//        n += 1
//        i += 2
//      }
//    }
//    assert(contextLength * (headSize / 2) == n)
//    new Pair[Array[Float], Array[Float]](cr, ci)
//  }
//}
//
//final class Vocabulary (tokens: Array[String], scores: Array[Float], tokenToIndex: util.Map[String, Integer]) {
//  this.tokens = tokens
//  this.scores = scores
//  this.tokenToIndex = tokenToIndex
//  final private val tokens: Array[String] = null
//  final private val scores: Array[Float] = null
//  final private val tokenToIndex: util.Map[String, Integer] = null
//
//  def this(vocabulary: Array[String], scores: Array[Float]) {
//    this(vocabulary, scores, IntStream.range(0, vocabulary.length).boxed.collect(Collectors.toMap((i: Integer) => vocabulary(i), (i: Integer) => i)))
//  }
//
//  def get(tokenIndex: Int): String = tokens(tokenIndex)
//
//  def getIndex(token: String): OptionalInt = {
//    val value = tokenToIndex.get(token)
//    if (value != null) OptionalInt.of(value)
//    else OptionalInt.empty
//  }
//
//  def size: Int = tokens.length
//}
//
//@FunctionalInterface object Sampler {
//  val ARGMAX: Sampler = FloatTensor.argmax
//}
//
//@FunctionalInterface trait Sampler {
//  def sampleToken(logits: FloatTensor): Int
//}
//
//final class CategoricalSampler (rng: RandomGenerator) extends Sampler {
//  this.rng = rng
//  final private val rng: RandomGenerator = null
//
//  override def sampleToken(logits: FloatTensor): Int = {
//    // sample index from probabilities (they must sum to 1!)
//    val random0to1 = rng.nextFloat(1f)
//    var cdf = 0.0f
//    for (i <- 0 until logits.size) {
//      cdf += logits.getFloat(i)
//      if (random0to1 < cdf) return i
//    }
//    logits.size - 1 // in case of rounding errors
//  }
//}
//
//object ToppSampler {
//  def swap(array: Array[Int], from: Int, to: Int): Unit = {
//    val tmp = array(from)
//    array(from) = array(to)
//    array(to) = tmp
//  }
//
//  def siftDown(array: Array[Int], from: Int, n: Int, comparator: Comparator[Integer]): Unit = {
//    var prev = from
//    var next = 0
//    while ((next = 2 * prev + 1) < n) {
//      val r = 2 * prev + 2
//      if (r < n && comparator.compare(array(r), array(next)) < 0) next = r
//      if (comparator.compare(array(next), array(prev)) < 0) {
//        swap(array, prev, next)
//        prev = next
//      }
//      else break //todo: break is not supported
//    }
//  }
//}
//
//final class ToppSampler(maxNumberOfElements: Int, val topp: Float, val rng: RandomGenerator) extends Sampler {
//  this.indices = new Array[Int](maxNumberOfElements)
//  final var indices: Array[Int] = null
//
//  override def sampleToken(logits: FloatTensor): Int = {
//    // top-p sampling (or "nucleus sampling") samples from the smallest set of
//    // tokens that exceed probability topp. This way we never sample tokens that
//    // have very low probabilities and are less likely to go "off the rails".
//    val comparator = Comparator.comparingDouble(logits.getFloat).reversed
//    val n = logits.size
//    var head = 0
//    var tail = n - 1
//    // values smaller than (1 - topp) / (n - 1) cannot be part of the result
//    // so for efficiency we crop these out as candidates before sorting
//    val cutoff = (1.0f - topp) / (n - 1)
//    for (i <- 0 until indices.length) {
//      if (logits.getFloat(i) >= cutoff) indices({
//        head += 1; head - 1
//      }) = i
//      else indices({
//        tail -= 1; tail + 1
//      }) = i
//    }
//    val n0 = head
//    // build heap O(n0)
//    for (i <- n0 / 2 - 1 to 0 by -1) {
//      ToppSampler.siftDown(indices, i, n0, comparator)
//    }
//    // truncate the list where cumulative probability of the largest k elements exceeds topp
//    // O(k lg n0)
//    var cumulativeProb = 0.0f
//    var lastIndex = 0
//    for (i <- n0 - 1 to 0 by -1) {
//      ToppSampler.swap(indices, 0, i)
//      cumulativeProb += logits.getFloat(indices(i))
//      if (cumulativeProb > topp) {
//        lastIndex = i
//        break //todo: break is not supported
//        // we've exceeded topp by including lastIndex
//      }
//      ToppSampler.siftDown(indices, 0, i - 1, comparator)
//    }
//    // sample from the truncated list
//    val r = rng.nextFloat(1f) * cumulativeProb
//    var cdf = 0.0f
//    for (i <- n0 - 1 to lastIndex by -1) {
//      cdf += logits.getFloat(indices(i))
//      if (r < cdf) return indices(i)
//    }
//    indices(lastIndex) // in case of rounding errors
//  }
//}
//
///**
// * Utility tailored for Llama 3 instruct prompt format.
// */
//object ChatFormat {
//  final class Message(role: ChatFormat.Role, content: String) {
//    this.role = role
//    this.content = content
//    final private val role: ChatFormat.Role = null
//    final private val content: String = null
//  }
//
//  object Role {
//    var SYSTEM = new ChatFormat.Role("system")
//    var USER = new ChatFormat.Role("user")
//    var ASSISTANT = new ChatFormat.Role("assistant")
//  }
//
//  final class Role(name: String) {
//    this.name = name
//    final private val name: String = null
//
//    override def toString: String = name
//  }
//}
//
//class ChatFormat(val tokenizer: Tokenizer) {
//  val specialTokens: util.Map[String, Integer] = this.tokenizer.getSpecialTokens
//  this.beginOfText = specialTokens.get("<|begin_of_text|>")
//  this.startHeader = specialTokens.get("<|start_header_id|>")
//  this.endHeader = specialTokens.get("<|end_header_id|>")
//  this.endOfTurn = specialTokens.get("<|eot_id|>")
//  this.endOfText = specialTokens.get("<|end_of_text|>")
//  this.endOfMessage = specialTokens.getOrDefault("<|eom_id|>", -1) // only in 3.1
//  this.stopTokens = util.Set.of(endOfText, endOfTurn)
//  final var beginOfText = 0
//  final var endHeader = 0
//  final var startHeader = 0
//  final var endOfTurn = 0
//  final var endOfText = 0
//  final var endOfMessage = 0
//  final var stopTokens: util.Set[Integer] = null
//
//  def getTokenizer: Tokenizer = tokenizer
//
//  def getStopTokens: util.Set[Integer] = stopTokens
//
//  def encodeHeader(message: ChatFormat.Message): util.List[Integer] = {
//    val tokens = new util.ArrayList[Integer]
//    tokens.add(startHeader)
//    tokens.addAll(this.tokenizer.encodeAsList(message.role.name))
//    tokens.add(endHeader)
//    tokens.addAll(this.tokenizer.encodeAsList("\n"))
//    tokens
//  }
//
//  def encodeMessage(message: ChatFormat.Message): util.List[Integer] = {
//    val tokens = this.encodeHeader(message)
//    tokens.addAll(this.tokenizer.encodeAsList(message.content.strip))
//    tokens.add(endOfTurn)
//    tokens
//  }
//
//  def encodeDialogPrompt(appendAssistantTurn: Boolean, dialog: util.List[ChatFormat.Message]): util.List[Integer] = {
//    val tokens = new util.ArrayList[Integer]
//    tokens.add(beginOfText)
//    import scala.collection.JavaConversions._
//    for (message <- dialog) {
//      tokens.addAll(this.encodeMessage(message))
//    }
//    if (appendAssistantTurn) {
//      // Add the start of an assistant message for the model to complete.
//      tokens.addAll(this.encodeHeader(new ChatFormat.Message(ChatFormat.Role.ASSISTANT, "")))
//    }
//    tokens
//  }
//}
//
///**
// * Support for AOT preloading of GGUF metadata with GraalVM's Native Image.
// *
// * <p>
// * To preload a model at build time, pass {@code -Dllama.PreloadGGUF=/path/to/model.gguf}
// * to the native-image builder command. At runtime, the preloaded model will be used
// * iff the specified and preloaded file names (base name) match.
// */
//object AOT {
//  final  class PartialModel (modelFileName: String, model: Llama, tensorDataOffset: Long, tensorInfos: util.Map[String, GGUF.GGUFTensorInfo]) {
//    this.modelFileName = modelFileName
//    this.model = model
//    this.tensorDataOffset = tensorDataOffset
//    this.tensorInfos = tensorInfos
//    final private val modelFileName: String = null
//    final private val model: Llama = null
//    final private val tensorDataOffset = 0L
//    final private val tensorInfos: util.Map[String, GGUF.GGUFTensorInfo] = null
//  }
//
//  private val PRELOADED_GGUF = preLoadGGUF(System.getProperty("llama.PreloadGGUF"))
//
//  private def preLoadGGUF(modelPath: String): AOT.PartialModel = {
//    if (modelPath == null || modelPath.isEmpty) return null
//    try {
//      val path = Path.of(modelPath)
//      if (!Files.exists(path) || !Files.isRegularFile(path)) throw new IllegalArgumentException("Cannot pre-load model: " + path)
//      val gguf = GGUF.loadModel(path)
//      try {
//        val fileChannel = FileChannel.open(path, StandardOpenOption.READ)
//        try new AOT.PartialModel(path.getFileName.toString, ModelLoader.loadModel(fileChannel, gguf, Llama3.Options.DEFAULT_MAX_TOKENS, false), gguf.getTensorDataOffset, gguf.getTensorInfos)
//        finally if (fileChannel != null) fileChannel.close()
//      }
//    } catch {
//      case e: IOException =>
//        throw new RuntimeException(e)
//    }
//  }
//
//  /**
//   * Tries to reuse a compatible AOT preloaded model.
//   * The file name (base name) must match with the preloaded file name.
//   * No checksum/hash is checked for performance reasons.
//   */
//  @throws[IOException]
//  def tryUsePreLoaded(modelPath: Path, contextLength: Int): Llama = {
//    val preLoaded = AOT.PRELOADED_GGUF
//    if (preLoaded == null) return null // no pre-loaded model stored
//    val optionsModel = modelPath.getFileName.toString
//    val preLoadedModel = preLoaded.modelFileName
//    if (!Objects.equals(optionsModel, preLoadedModel)) {
//      // Preloaded and specified model file names didn't match.
//      return null
//    }
//    val baseModel = preLoaded.model
//    try {
//      val timer = Timer.log("Load tensors from pre-loaded model")
//      val fileChannel = FileChannel.open(modelPath, StandardOpenOption.READ)
//      try {
//        // Load only the tensors (mmap slices).
//        val tensorEntries = GGUF.loadTensors(fileChannel, preLoaded.tensorDataOffset, preLoaded.tensorInfos)
//        val weights = ModelLoader.loadWeights(tensorEntries, baseModel.configuration)
//        new Llama(baseModel.configuration.withContextLength(contextLength), baseModel.tokenizer, weights)
//      } finally {
//        if (timer != null) timer.close()
//        if (fileChannel != null) fileChannel.close()
//      }
//    }
//  }
//}